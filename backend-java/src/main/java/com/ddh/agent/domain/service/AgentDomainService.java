package com.ddh.agent.domain.service;

import com.ddh.agent.domain.model.conversation.Conversation;
import com.ddh.agent.domain.model.conversation.ConversationRepository;
import com.ddh.agent.domain.model.conversation.ConversationTable;
import com.ddh.agent.domain.model.conversation.Message;
import com.ddh.agent.domain.model.table.SourceTable;
import com.ddh.agent.domain.model.table.SourceTableRepository;
import com.ddh.agent.domain.model.table.TableColumn;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AgentDomainService {

    @Autowired
    private LlmPort llmPort;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private SourceTableRepository sourceTableRepository;
    @Autowired
    private EtlDomainService etlDomainService;
    @Autowired
    private RelationDomainService relationDomainService;

    private final ObjectMapper mapper = new ObjectMapper();

    // ── 工具定义（对应 Python AGENT_TOOLS）────────────────────────────────────

    private static final List<Map<String, Object>> ALL_TOOLS = Arrays.asList(
            tool("list_project_tables", "Get all source tables selected for this conversation.",
                    noParams()),
            tool("get_table_schema", "Get column definitions (name, type, comment) for a source table.",
                    paramsSingle("table_id", "integer", "The table ID")),
            tool("propose_schema",
                    "Propose the target table structure to the user for confirmation. " +
                            "Call this when you have determined the output table columns from the requirements.",
                    paramsForProposeSchema()),
            tool("propose_etl_steps",
                    "Propose the ETL execution plan to the user for confirmation. " +
                            "Call this after schema has been confirmed.",
                    paramsForProposeEtlSteps()),
            tool("generate_sql",
                    "Generate GaussDB SQL for one ETL step. The SQL must be complete and executable, " +
                            "including any required DDL: CREATE TABLE for the target-table step, " +
                            "CREATE TEMPORARY TABLE for steps where is_temp_table=true.",
                    paramsForGenerateSql())
    );

    private static final Map<Integer, List<String>> TOOLS_BY_STATE = new HashMap<>();

    static {
        TOOLS_BY_STATE.put(1, Arrays.asList("list_project_tables", "get_table_schema", "propose_schema"));
        TOOLS_BY_STATE.put(3, Collections.singletonList("propose_etl_steps"));
        TOOLS_BY_STATE.put(4, Collections.singletonList("generate_sql"));
    }

    // ── 主入口 ────────────────────────────────────────────────────────────────

    /**
     * 执行 Agent 循环。emit 将事件推送到调用方（SSE 层）。
     * 返回 state=4 生成的 SQL 步骤（含 file_path），由 AgentAppService 持久化 EtlJob/EtlStep。
     */
    public List<Map<String, Object>> run(Long conversationId,
                                         Consumer<Map<String, Object>> emit) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        int state = conv.getState();
        if (state == 5) {
            emit.accept(map("type", "already_done"));
            return Collections.emptyList();
        }

        List<Map<String, Object>> messages = buildHistory(conversationId);
        if (messages.isEmpty()) {
            emit.accept(map("type", "error", "message", "No user message to process"));
            return Collections.emptyList();
        }

        // state=2（已提出 schema、等待确认）若用户继续发消息，按需求收集模式（state 1）重新跑。
        int runState = (state == 2) ? 1 : state;
        String system = buildSystemPrompt(runState);
        List<Map<String, Object>> tools = getToolsForState(runState);
        List<Map<String, Object>> generatedSteps = new ArrayList<>();

        // Tool-use loop
        while (true) {
            LlmPort.LlmResponse response = llmPort.chatWithToolsStream(
                    messages, tools, system,
                    delta -> emit.accept(map("type", "token", "text", delta)));

            emit.accept(map("type", "turn_end"));

            if ("end_turn".equals(response.stopReason)) {
                if (response.text != null && !response.text.isEmpty()) {
                    saveAssistantMessage(conversationId, response.text);
                }
                break;
            }

            messages.add(response.toAssistantMessage());

            for (LlmPort.ToolCall toolCall : response.toolCalls) {
                Map<String, Object> result = executeTool(
                        toolCall.name, toolCall.input, conv, emit);

                if ("generate_sql".equals(toolCall.name)
                        && "sql_saved".equals(result.get("status"))) {
                    generatedSteps.add(result);
                }

                Map<String, Object> toolResultMsg = new HashMap<>();
                toolResultMsg.put("role", "tool_result");
                toolResultMsg.put("tool_call_id", toolCall.id);
                try {
                    toolResultMsg.put("content", mapper.writeValueAsString(result));
                } catch (JsonProcessingException e) {
                    toolResultMsg.put("content", "{}");
                }
                messages.add(toolResultMsg);
            }
        }

        return generatedSteps;
    }

    // ── 工具执行 ──────────────────────────────────────────────────────────────

    private Map<String, Object> executeTool(String name,
                                            Map<String, Object> input,
                                            Conversation conv,
                                            Consumer<Map<String, Object>> emit) {
        switch (name) {
            case "list_project_tables": {
                List<ConversationTable> rows =
                        conversationRepository.findTablesByConversationId(conv.getId());
                List<Map<String, Object>> tables = rows.stream().map(ct -> {
                    Optional<SourceTable> t = sourceTableRepository.findById(ct.getTableId());
                    if (!t.isPresent()) return null;
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", t.get().getId());
                    m.put("name", t.get().getName());
                    m.put("scope", t.get().getScope());
                    return m;
                }).filter(Objects::nonNull).collect(Collectors.toList());
                Map<String, Object> result = new HashMap<>();
                result.put("tables", tables);
                List<Long> tableIds = rows.stream()
                        .map(ConversationTable::getTableId).collect(Collectors.toList());
                String relationText = relationDomainService.buildRelationTextForTables(tableIds);
                if (relationText != null && !relationText.isEmpty()) {
                    result.put("relations", relationText);
                }
                return result;
            }

            case "get_table_schema": {
                Long tableId = toLong(input.get("table_id"));
                Optional<SourceTable> table = sourceTableRepository.findById(tableId);
                if (!table.isPresent()) {
                    return map("error", "Table " + tableId + " not found");
                }
                List<TableColumn> cols = sourceTableRepository.findColumnsByTableId(tableId);
                List<Map<String, Object>> colList = cols.stream().map(c -> {
                    Map<String, Object> cm = new HashMap<>();
                    cm.put("column_name", c.getColumnName());
                    cm.put("data_type", c.getDataType());
                    cm.put("comment", c.getComment());
                    return cm;
                }).collect(Collectors.toList());
                Map<String, Object> result = new HashMap<>();
                result.put("name", table.get().getName());
                result.put("columns", colList);
                return result;
            }

            case "propose_schema": {
                conv.setState(2);
                conversationRepository.save(conv);
                Map<String, Object> event = new HashMap<>();
                event.put("type", "schema_proposal");
                event.put("target_table", input.get("target_table"));
                event.put("columns", input.get("columns"));
                emit.accept(event);
                return map("status", "proposal_sent");
            }

            case "propose_etl_steps": {
                conv.setState(3);
                conversationRepository.save(conv);
                Map<String, Object> event = new HashMap<>();
                event.put("type", "steps_proposal");
                event.put("steps", input.get("steps"));
                emit.accept(event);
                return map("status", "proposal_sent");
            }

            case "generate_sql": {
                int stepOrder = toInt(input.get("step_order"));
                String stepName = String.valueOf(input.get("step_name"));
                boolean isTempTable = Boolean.TRUE.equals(input.get("is_temp_table"));
                String sql = String.valueOf(input.get("sql"));

                String filePath = etlDomainService.writeSqlFile(
                        conv.getProjectId(), stepOrder, stepName, sql);

                Map<String, Object> genEvent = new HashMap<>();
                genEvent.put("type", "step_generated");
                genEvent.put("step_order", stepOrder);
                genEvent.put("step_name", stepName);
                genEvent.put("sql", sql);
                genEvent.put("file", filePath);
                emit.accept(genEvent);

                Map<String, Object> result = new HashMap<>();
                result.put("status", "sql_saved");
                result.put("step_order", stepOrder);
                result.put("step_name", stepName);
                result.put("is_temp_table", isTempTable);
                result.put("sql", sql);
                result.put("file_path", filePath);
                result.put("project_id", conv.getProjectId());
                return result;
            }

            default:
                return map("error", "Unknown tool: " + name);
        }
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildHistory(Long conversationId) {
        return conversationRepository.findMessagesByConversationId(conversationId)
                .stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .map(m -> map("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());
    }

    private void saveAssistantMessage(Long conversationId, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        conversationRepository.saveMessage(msg);
    }

    private List<Map<String, Object>> getToolsForState(int state) {
        List<String> names = TOOLS_BY_STATE.getOrDefault(state, Collections.emptyList());
        return ALL_TOOLS.stream()
                .filter(t -> names.contains(t.get("name")))
                .collect(Collectors.toList());
    }

    private String buildSystemPrompt(int state) {
        String base = "You are an ETL development assistant for GaussDB data warehouses. " +
                "GaussDB uses PostgreSQL-compatible SQL syntax. " +
                "Always generate complete, executable SQL. ";
        switch (state) {
            case 1:
                return base +
                        "Your task: analyze the user's ETL requirements. " +
                        "Use list_project_tables to see available source tables; its result also " +
                        "includes a `relations` section describing predefined relationships between " +
                        "these tables (relation type and join column pairs). " +
                        "Use get_table_schema to understand column details. " +
                        "When planning how to combine tables, prefer the join keys given in `relations`. " +
                        "When you fully understand the requirements, call propose_schema " +
                        "to propose the target table structure. " +
                        "IMPORTANT: If you have already proposed a schema and the user subsequently " +
                        "requests changes (adding, removing, or modifying columns), you MUST call " +
                        "propose_schema again with the updated target_table and columns — do not just " +
                        "acknowledge the request in text.";
            case 3:
                return base +
                        "The target table schema has been confirmed (shown in conversation history). " +
                        "Your task: plan the COMPLETE ETL execution steps for a data-warehouse job. " +
                        "A complete plan MUST explicitly include, as separate steps: " +
                        "(1) a step that CREATES THE TARGET TABLE using the confirmed schema (is_temp_table=false); " +
                        "(2) the data cleaning / transformation steps; " +
                        "(3) a step to CREATE each TEMPORARY TABLE you decide to use (is_temp_table=true) — " +
                        "only when temporary tables are actually needed; if none are needed, omit them entirely. " +
                        "Order the steps so every temporary table and the target table are created BEFORE any step writes into them. " +
                        "Call propose_etl_steps with this complete plan.";
            case 4:
                return base +
                        "The ETL execution steps have been confirmed (shown in conversation history). " +
                        "Your task: generate GaussDB SQL for each step. " +
                        "Call generate_sql once per step in the order they appear in the confirmed plan. " +
                        "Each step's SQL must be complete and executable: " +
                        "the target-table-creation step must emit CREATE TABLE for the target table using the confirmed schema; " +
                        "every step marked is_temp_table=true must emit CREATE TEMPORARY TABLE for that step's output table; " +
                        "the cleaning/load steps must emit the transformation SQL (e.g. INSERT INTO ... SELECT) " +
                        "that writes into the temporary or target tables.";
            default:
                return base;
        }
    }

    // ── 工具定义构建器 ────────────────────────────────────────────────────────

    private static Map<String, Object> tool(String name, String desc,
                                            Map<String, Object> parameters) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("description", desc);
        t.put("parameters", parameters);
        return t;
    }

    private static Map<String, Object> noParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "object");
        p.put("properties", new LinkedHashMap<>());
        return p;
    }

    private static Map<String, Object> paramsSingle(String name, String type, String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(name, prop(type, desc));
        p.put("properties", props);
        p.put("required", Collections.singletonList(name));
        return p;
    }

    private static Map<String, Object> prop(String type, String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        if (desc != null && !desc.isEmpty()) p.put("description", desc);
        return p;
    }

    private static Map<String, Object> paramsForProposeSchema() {
        Map<String, Object> columnItem = new LinkedHashMap<>();
        columnItem.put("type", "object");
        Map<String, Object> colProps = new LinkedHashMap<>();
        colProps.put("name", prop("string", ""));
        colProps.put("type", prop("string", ""));
        colProps.put("comment", prop("string", ""));
        columnItem.put("properties", colProps);
        columnItem.put("required", Arrays.asList("name", "type"));

        Map<String, Object> columnsArr = new LinkedHashMap<>();
        columnsArr.put("type", "array");
        columnsArr.put("items", columnItem);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("target_table", prop("string", "Name of the output target table"));
        props.put("columns", columnsArr);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "object");
        p.put("properties", props);
        p.put("required", Arrays.asList("target_table", "columns"));
        return p;
    }

    private static Map<String, Object> paramsForProposeEtlSteps() {
        Map<String, Object> stepItem = new LinkedHashMap<>();
        stepItem.put("type", "object");
        Map<String, Object> stepProps = new LinkedHashMap<>();
        stepProps.put("step_order", prop("integer", ""));
        stepProps.put("step_name", prop("string", ""));
        stepProps.put("description", prop("string", ""));
        stepProps.put("is_temp_table", prop("boolean", ""));
        stepProps.put("output_table", prop("string", ""));
        stepItem.put("properties", stepProps);
        stepItem.put("required", Arrays.asList("step_order", "step_name", "description",
                "is_temp_table", "output_table"));

        Map<String, Object> stepsArr = new LinkedHashMap<>();
        stepsArr.put("type", "array");
        stepsArr.put("items", stepItem);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("steps", stepsArr);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "object");
        p.put("properties", props);
        p.put("required", Collections.singletonList("steps"));
        return p;
    }

    private static Map<String, Object> paramsForGenerateSql() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("step_order", prop("integer", ""));
        props.put("step_name", prop("string", ""));
        props.put("is_temp_table", prop("boolean", ""));
        props.put("sql", prop("string", "Complete GaussDB SQL for this step"));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "object");
        p.put("properties", props);
        p.put("required", Arrays.asList("step_order", "step_name", "is_temp_table", "sql"));
        return p;
    }

    // ── 类型转换工具 ──────────────────────────────────────────────────────────

    private static Long toLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.valueOf(v.toString());
    }

    private static int toInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.parseInt(v.toString());
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
