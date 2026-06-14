# DDH Agent Java 迁移 — Plan C：LLM 适配器 + Agent 状态机 + SSE 流式

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**前置条件：** Plan A、Plan B 已完成。

**Goal:** 实现三家 LLM Provider 适配器、Agent 状态机（工具调用循环）、ETL 文件生成服务，并将 `GET /conversations/{id}/stream` SSE 端点接入 `ConversationController`，使前端完整 AI 对话流程可用。

**Architecture:** `infrastructure/llm/` 实现 `domain/service/LlmPort`；`domain/service/AgentDomainService` 持有状态机逻辑；`application/service/AgentAppService` 通过 `SseEmitter` 在线程池中异步调用 Agent，推送事件到前端。

**Tech Stack:** OkHttp3（HTTP 调用 LLM API）、Jackson（JSON 序列化）、Spring SseEmitter、Java ExecutorService

---

## 文件清单

```
backend-java/src/main/java/com/ddh/agent/
├── infrastructure/
│   └── llm/
│       ├── ClaudeAdapter.java
│       ├── DeepSeekAdapter.java
│       └── QwenAdapter.java
├── infrastructure/config/
│   └── LlmConfig.java                    # @ConfigurationProperties + LlmPort @Bean
├── domain/service/
│   ├── AgentDomainService.java           # 状态机 + 工具调用循环
│   └── EtlDomainService.java             # SQL/MD 文件写入 + EtlJob/EtlStep 持久化
└── application/service/
    └── AgentAppService.java              # SseEmitter + 线程池调度
```

`ConversationController.java` 新增 `/stream` 端点（在已有文件上追加方法）。

---

## Task 14：LlmConfig + ClaudeAdapter

**Files:**
- Create: `infrastructure/config/LlmConfig.java`
- Create: `infrastructure/llm/ClaudeAdapter.java`

- [ ] **Step 1: 创建 `LlmConfig.java`**

读取 `application.yml` 中 `llm.*` 配置，并根据 `llm.provider` 注入对应的 `LlmPort` Bean。

```java
package com.ddh.agent.infrastructure.config;

import com.ddh.agent.domain.service.LlmPort;
import com.ddh.agent.infrastructure.llm.ClaudeAdapter;
import com.ddh.agent.infrastructure.llm.DeepSeekAdapter;
import com.ddh.agent.infrastructure.llm.QwenAdapter;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Value("${llm.provider}") private String provider;
    @Value("${llm.claude.api-key:}") private String claudeApiKey;
    @Value("${llm.claude.model:claude-sonnet-4-6}") private String claudeModel;
    @Value("${llm.deepseek.api-key:}") private String deepseekApiKey;
    @Value("${llm.deepseek.model:deepseek-chat}") private String deepseekModel;
    @Value("${llm.qwen.api-key:}") private String qwenApiKey;
    @Value("${llm.qwen.model:qwen-max}") private String qwenModel;

    @Autowired private OkHttpClient okHttpClient;

    @Bean
    public LlmPort llmPort() {
        switch (provider) {
            case "deepseek":
                return new DeepSeekAdapter(okHttpClient, deepseekApiKey, deepseekModel);
            case "qwen":
                return new QwenAdapter(okHttpClient, qwenApiKey, qwenModel);
            default:
                return new ClaudeAdapter(okHttpClient, claudeApiKey, claudeModel);
        }
    }
}
```

- [ ] **Step 2: 创建 `ClaudeAdapter.java`**

调用 Anthropic Messages API，将工具调用结果映射到 `LlmPort.LlmResponse`。

```java
package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import java.io.IOException;
import java.util.*;

public class ClaudeAdapter implements LlmPort {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudeAdapter(OkHttpClient client, String apiKey, String model) {
        this.client = client;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public LlmResponse chatWithTools(List<Map<String, Object>> messages,
                                     List<Map<String, Object>> tools,
                                     String systemPrompt) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 4096);
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                body.put("system", systemPrompt);
            }

            // messages
            ArrayNode msgs = body.putArray("messages");
            for (Map<String, Object> m : messages) {
                String role = (String) m.get("role");
                // tool_result → 包装成 Anthropic tool_result content block
                if ("tool_result".equals(role)) {
                    ObjectNode toolResultMsg = mapper.createObjectNode();
                    toolResultMsg.put("role", "user");
                    ArrayNode content = toolResultMsg.putArray("content");
                    ObjectNode block = content.addObject();
                    block.put("type", "tool_result");
                    block.put("tool_use_id", (String) m.get("tool_call_id"));
                    block.put("content", (String) m.get("content"));
                    msgs.add(toolResultMsg);
                } else {
                    ObjectNode msg = mapper.createObjectNode();
                    msg.put("role", role);
                    Object contentObj = m.get("content");
                    if (contentObj instanceof String) {
                        msg.put("content", (String) contentObj);
                    } else if (contentObj instanceof List) {
                        // assistant message with tool_calls
                        msg.set("content", mapper.valueToTree(contentObj));
                    }
                    // tool_calls in assistant message
                    if (m.containsKey("tool_calls")) {
                        msg.set("content", buildAssistantContent(
                            (String) contentObj, (List<?>) m.get("tool_calls")));
                    }
                    msgs.add(msg);
                }
            }

            // tools
            if (tools != null && !tools.isEmpty()) {
                body.set("tools", mapper.valueToTree(tools));
            }

            Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Claude API error " + response.code()
                        + ": " + responseBody);
                }
                return parseResponse(responseBody);
            }
        } catch (IOException e) {
            throw new RuntimeException("Claude API call failed", e);
        }
    }

    private ArrayNode buildAssistantContent(String text, List<?> toolCalls) {
        ArrayNode content = mapper.createArrayNode();
        if (text != null && !text.isEmpty()) {
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", text);
        }
        for (Object tc : toolCalls) {
            if (tc instanceof LlmPort.ToolCall) {
                LlmPort.ToolCall call = (LlmPort.ToolCall) tc;
                ObjectNode toolBlock = content.addObject();
                toolBlock.put("type", "tool_use");
                toolBlock.put("id", call.id);
                toolBlock.put("name", call.name);
                toolBlock.set("input", mapper.valueToTree(call.input));
            }
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseResponse(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        String stopReason = root.path("stop_reason").asText("end_turn");
        String text = null;
        List<LlmPort.ToolCall> toolCalls = new ArrayList<>();

        JsonNode contentArray = root.path("content");
        for (JsonNode block : contentArray) {
            String type = block.path("type").asText();
            if ("text".equals(type)) {
                text = block.path("text").asText();
            } else if ("tool_use".equals(type)) {
                String id = block.path("id").asText();
                String name = block.path("name").asText();
                Map<String, Object> input = mapper.convertValue(
                    block.path("input"), Map.class);
                toolCalls.add(new LlmPort.ToolCall(id, name, input));
            }
        }
        return new LlmResponse(text, stopReason, toolCalls);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add LlmConfig and ClaudeAdapter (Anthropic Messages API)"
```

---

## Task 15：DeepSeekAdapter + QwenAdapter

**Files:**
- Create: `infrastructure/llm/DeepSeekAdapter.java`
- Create: `infrastructure/llm/QwenAdapter.java`

两者均实现 OpenAI 兼容接口（`/v1/chat/completions`），差异仅在 base URL 和认证头。

- [ ] **Step 1: 创建 `DeepSeekAdapter.java`**

```java
package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import okhttp3.*;
import java.io.IOException;
import java.util.*;

public class DeepSeekAdapter implements LlmPort {

    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeepSeekAdapter(OkHttpClient client, String apiKey, String model) {
        this.client = client;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public LlmResponse chatWithTools(List<Map<String, Object>> messages,
                                     List<Map<String, Object>> tools,
                                     String systemPrompt) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);

            ArrayNode msgs = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                ObjectNode sys = msgs.addObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
            }
            for (Map<String, Object> m : messages) {
                msgs.add(toOpenAiMessage(m));
            }

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsNode = body.putArray("tools");
                for (Map<String, Object> t : tools) {
                    ObjectNode tool = toolsNode.addObject();
                    tool.put("type", "function");
                    ObjectNode fn = tool.putObject("function");
                    fn.put("name", (String) t.get("name"));
                    fn.put("description", (String) t.get("description"));
                    fn.set("parameters", mapper.valueToTree(t.get("parameters")));
                }
            }

            Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("DeepSeek API error " + response.code()
                        + ": " + responseBody);
                }
                return parseOpenAiResponse(responseBody);
            }
        } catch (IOException e) {
            throw new RuntimeException("DeepSeek API call failed", e);
        }
    }

    private JsonNode toOpenAiMessage(Map<String, Object> m) {
        ObjectNode msg = mapper.createObjectNode();
        String role = (String) m.get("role");
        if ("tool_result".equals(role)) {
            msg.put("role", "tool");
            msg.put("tool_call_id", (String) m.get("tool_call_id"));
            msg.put("content", (String) m.get("content"));
        } else {
            msg.put("role", role);
            Object content = m.get("content");
            msg.put("content", content instanceof String ? (String) content : "");
            if (m.containsKey("tool_calls")) {
                ArrayNode tcs = msg.putArray("tool_calls");
                for (Object tc : (List<?>) m.get("tool_calls")) {
                    if (tc instanceof LlmPort.ToolCall) {
                        LlmPort.ToolCall call = (LlmPort.ToolCall) tc;
                        ObjectNode tcNode = tcs.addObject();
                        tcNode.put("id", call.id);
                        tcNode.put("type", "function");
                        ObjectNode fn = tcNode.putObject("function");
                        fn.put("name", call.name);
                        try { fn.put("arguments", mapper.writeValueAsString(call.input)); }
                        catch (Exception e) { fn.put("arguments", "{}"); }
                    }
                }
            }
        }
        return msg;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseOpenAiResponse(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode choice = root.path("choices").path(0);
        String finishReason = choice.path("finish_reason").asText("stop");
        String stopReason = "tool_calls".equals(finishReason) ? "tool_use" : "end_turn";

        JsonNode msgNode = choice.path("message");
        String text = msgNode.path("content").asText(null);
        List<LlmPort.ToolCall> toolCalls = new ArrayList<>();

        for (JsonNode tc : msgNode.path("tool_calls")) {
            String id = tc.path("id").asText();
            String name = tc.path("function").path("name").asText();
            String argsStr = tc.path("function").path("arguments").asText("{}");
            Map<String, Object> input = mapper.readValue(argsStr, Map.class);
            toolCalls.add(new LlmPort.ToolCall(id, name, input));
        }
        return new LlmResponse(text, stopReason, toolCalls);
    }
}
```

- [ ] **Step 2: 创建 `QwenAdapter.java`**

Qwen 使用阿里云 DashScope OpenAI 兼容端点，逻辑与 DeepSeek 相同，只需改 URL 和认证方式。

```java
package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import java.util.List;
import java.util.Map;

/** Qwen via DashScope OpenAI-compatible endpoint */
public class QwenAdapter extends DeepSeekAdapter {

    private static final String QWEN_URL =
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    public QwenAdapter(OkHttpClient client, String apiKey, String model) {
        super(client, apiKey, model);
        // 替换父类 API_URL — 通过重写 chatWithTools 实现
    }

    // QwenAdapter 直接继承 DeepSeekAdapter 逻辑，但 URL 不同。
    // 由于 DeepSeekAdapter.API_URL 是私有常量，需要提取为受保护方法。
    // 实现策略：在 DeepSeekAdapter 中将 buildRequest 提取为 protected 方法，
    // 此处 QwenAdapter 覆盖该方法传入 QWEN_URL。
    //
    // 简单实现：直接复制 DeepSeekAdapter 代码，修改 API_URL 字段即可。
    // 为避免重复，将公共逻辑提取到 OpenAiCompatibleAdapter 基类（见 Step 3）。
}
```

> **注意：** Step 2 是过渡占位。Step 3 将重构为公共基类，QwenAdapter 只需两行代码。

- [ ] **Step 3: 重构 — 提取 `OpenAiCompatibleAdapter` 基类**

将 `DeepSeekAdapter` 中的核心逻辑移入基类，`DeepSeekAdapter` 和 `QwenAdapter` 只传入各自的 URL：

```java
// infrastructure/llm/OpenAiCompatibleAdapter.java
package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import okhttp3.*;
import java.io.IOException;
import java.util.*;

public abstract class OpenAiCompatibleAdapter implements LlmPort {

    protected static final MediaType JSON =
        MediaType.get("application/json; charset=utf-8");

    protected final OkHttpClient client;
    protected final String apiKey;
    protected final String model;
    protected final String apiUrl;
    protected final ObjectMapper mapper = new ObjectMapper();

    protected OpenAiCompatibleAdapter(OkHttpClient client, String apiKey,
                                      String model, String apiUrl) {
        this.client = client;
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = apiUrl;
    }

    @Override
    public LlmResponse chatWithTools(List<Map<String, Object>> messages,
                                     List<Map<String, Object>> tools,
                                     String systemPrompt) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);

            ArrayNode msgs = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                msgs.addObject().put("role", "system").put("content", systemPrompt);
            }
            for (Map<String, Object> m : messages) {
                msgs.add(toOpenAiMessage(m));
            }
            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsNode = body.putArray("tools");
                for (Map<String, Object> t : tools) {
                    ObjectNode tool = toolsNode.addObject();
                    tool.put("type", "function");
                    ObjectNode fn = tool.putObject("function");
                    fn.put("name", (String) t.get("name"));
                    fn.put("description", (String) t.get("description"));
                    fn.set("parameters", mapper.valueToTree(t.get("parameters")));
                }
            }

            Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .build();

            try (Response response = client.newCall(request).execute()) {
                String rb = response.body().string();
                if (!response.isSuccessful()) {
                    throw new RuntimeException(
                        apiUrl + " error " + response.code() + ": " + rb);
                }
                return parseOpenAiResponse(rb);
            }
        } catch (IOException e) {
            throw new RuntimeException("LLM API call failed: " + apiUrl, e);
        }
    }

    private JsonNode toOpenAiMessage(Map<String, Object> m) {
        ObjectNode msg = mapper.createObjectNode();
        String role = (String) m.get("role");
        if ("tool_result".equals(role)) {
            msg.put("role", "tool");
            msg.put("tool_call_id", (String) m.get("tool_call_id"));
            msg.put("content", (String) m.get("content"));
        } else {
            msg.put("role", role);
            Object content = m.get("content");
            msg.put("content", content instanceof String ? (String) content : "");
            if (m.containsKey("tool_calls")) {
                ArrayNode tcs = msg.putArray("tool_calls");
                for (Object tc : (List<?>) m.get("tool_calls")) {
                    if (tc instanceof ToolCall) {
                        ToolCall call = (ToolCall) tc;
                        ObjectNode tcNode = tcs.addObject();
                        tcNode.put("id", call.id);
                        tcNode.put("type", "function");
                        ObjectNode fn = tcNode.putObject("function");
                        fn.put("name", call.name);
                        try { fn.put("arguments", mapper.writeValueAsString(call.input)); }
                        catch (Exception e) { fn.put("arguments", "{}"); }
                    }
                }
            }
        }
        return msg;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseOpenAiResponse(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode choice = root.path("choices").path(0);
        String finishReason = choice.path("finish_reason").asText("stop");
        String stopReason = "tool_calls".equals(finishReason) ? "tool_use" : "end_turn";
        JsonNode msgNode = choice.path("message");
        String text = msgNode.path("content").asText(null);
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode tc : msgNode.path("tool_calls")) {
            String id = tc.path("id").asText();
            String name = tc.path("function").path("name").asText();
            String argsStr = tc.path("function").path("arguments").asText("{}");
            Map<String, Object> input = mapper.readValue(argsStr, Map.class);
            toolCalls.add(new ToolCall(id, name, input));
        }
        return new LlmResponse(text, stopReason, toolCalls);
    }
}
```

重写 `DeepSeekAdapter` 和 `QwenAdapter`（各两行）：

```java
// DeepSeekAdapter.java
package com.ddh.agent.infrastructure.llm;
import okhttp3.OkHttpClient;
public class DeepSeekAdapter extends OpenAiCompatibleAdapter {
    public DeepSeekAdapter(OkHttpClient client, String apiKey, String model) {
        super(client, apiKey, model, "https://api.deepseek.com/v1/chat/completions");
    }
}

// QwenAdapter.java
package com.ddh.agent.infrastructure.llm;
import okhttp3.OkHttpClient;
public class QwenAdapter extends OpenAiCompatibleAdapter {
    public QwenAdapter(OkHttpClient client, String apiKey, String model) {
        super(client, apiKey, model,
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add DeepSeek/Qwen adapters via OpenAI-compatible base class"
```

---

## Task 16：AgentDomainService — 工具定义 + 状态机

**Files:**
- Create: `domain/service/AgentDomainService.java`

实现对应 Python `agent_service.py` + `agent_tools.py` 的核心逻辑：消息历史构建、工具调用循环、五种工具执行。

- [ ] **Step 1: 创建 `AgentDomainService.java`**

```java
package com.ddh.agent.domain.service;

import com.ddh.agent.domain.model.conversation.*;
import com.ddh.agent.domain.model.project.ProjectRepository;
import com.ddh.agent.domain.model.project.ProjectTable;
import com.ddh.agent.domain.model.table.*;
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

    @Autowired private LlmPort llmPort;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SourceTableRepository sourceTableRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    // ── 工具定义（对应 Python AGENT_TOOLS）────────────────────────────────────

    private static final List<Map<String, Object>> ALL_TOOLS = Arrays.asList(
        tool("list_project_tables", "Get all source tables selected for this ETL project.",
            params(prop("project_id", "integer", "The project ID"), "project_id")),
        tool("get_table_schema", "Get column definitions (name, type, comment) for a source table.",
            params(prop("table_id", "integer", "The table ID"), "table_id")),
        tool("propose_schema",
            "Propose the target table structure to the user for confirmation. " +
            "Call this when you have determined the output table columns from the requirements.",
            paramsForProposeSchema()),
        tool("propose_etl_steps",
            "Propose the ETL execution plan to the user for confirmation. " +
            "Call this after schema has been confirmed.",
            paramsForProposeEtlSteps()),
        tool("generate_sql", "Generate GaussDB SQL for one ETL step. Call once per confirmed step.",
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
     * generatedSteps 收集 state=4 生成的 SQL 步骤，由 EtlDomainService 后续持久化。
     */
    public List<Map<String, Object>> run(Long conversationId,
                                         Consumer<Map<String, Object>> emit) {
        Conversation conv = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        int state = conv.getState();

        if (state == 2) {
            emit.accept(map("type", "waiting", "state", state,
                "message", "Waiting for schema confirmation"));
            return Collections.emptyList();
        }
        if (state == 5) {
            emit.accept(map("type", "already_done"));
            return Collections.emptyList();
        }

        List<Map<String, Object>> messages = buildHistory(conversationId);
        if (messages.isEmpty()) {
            emit.accept(map("type", "error", "message", "No user message to process"));
            return Collections.emptyList();
        }

        String system = buildSystemPrompt(state);
        List<Map<String, Object>> tools = getToolsForState(state);
        List<Map<String, Object>> generatedSteps = new ArrayList<>();

        // Tool-use loop
        while (true) {
            LlmPort.LlmResponse response = llmPort.chatWithTools(messages, tools, system);

            if (response.text != null && !response.text.isEmpty()) {
                emit.accept(map("type", "token", "text", response.text));
            }

            if ("end_turn".equals(response.stopReason)) {
                if (response.text != null && !response.text.isEmpty()) {
                    saveAssistantMessage(conversationId, response.text);
                }
                break;
            }

            // 将 assistant 消息（含 tool_calls）加入历史
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
                Long projectId = toLong(input.get("project_id"));
                List<ProjectTable> rows = projectRepository.findTablesByProjectId(projectId);
                List<Map<String, Object>> tables = rows.stream().map(pt -> {
                    Optional<SourceTable> t = sourceTableRepository.findById(pt.getTableId());
                    if (!t.isPresent()) return null;
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", t.get().getId());
                    m.put("name", t.get().getName());
                    m.put("scope", t.get().getScope());
                    return m;
                }).filter(Objects::nonNull).collect(Collectors.toList());
                return map("tables", tables);
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
                // state 保持 3（等待 confirm-steps 推进到 4）
                Map<String, Object> event = new HashMap<>();
                event.put("type", "steps_proposal");
                event.put("steps", input.get("steps"));
                emit.accept(event);
                return map("status", "proposal_sent");
            }

            case "generate_sql": {
                // 通知 EtlDomainService 写文件（通过 emit 携带数据）
                int stepOrder = toInt(input.get("step_order"));
                String stepName = (String) input.get("step_name");
                boolean isTempTable = Boolean.TRUE.equals(input.get("is_temp_table"));
                String sql = (String) input.get("sql");

                Map<String, Object> genEvent = new HashMap<>();
                genEvent.put("type", "step_generated");
                genEvent.put("step_order", stepOrder);
                genEvent.put("step_name", stepName);
                genEvent.put("sql", sql);
                emit.accept(genEvent);

                // 返回给 AgentAppService 用于持久化
                Map<String, Object> result = new HashMap<>();
                result.put("status", "sql_saved");
                result.put("step_order", stepOrder);
                result.put("step_name", stepName);
                result.put("is_temp_table", isTempTable);
                result.put("sql", sql);
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
            case 1: return base +
                "Your task: analyze the user's ETL requirements. " +
                "Use list_project_tables to see available source tables, " +
                "use get_table_schema to understand column details. " +
                "When you fully understand the requirements, call propose_schema " +
                "to propose the target table structure.";
            case 3: return base +
                "The target table schema has been confirmed (shown in conversation history). " +
                "Your task: plan the ETL execution steps. Consider whether temporary tables are needed. " +
                "Call propose_etl_steps with the complete execution plan.";
            case 4: return base +
                "The ETL execution steps have been confirmed (shown in conversation history). " +
                "Your task: generate GaussDB SQL for each step. " +
                "Call generate_sql once per step in the order they appear in the confirmed plan.";
            default: return base;
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

    private static Map<String, Object> params(Map<String, Object> prop, String... required) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(required[0], prop);
        p.put("properties", props);
        p.put("required", Arrays.asList(required));
        return p;
    }

    private static Map<String, Object> prop(String name, String type, String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }

    private static Map<String, Object> paramsForProposeSchema() {
        Map<String, Object> columnItem = new LinkedHashMap<>();
        columnItem.put("type", "object");
        Map<String, Object> colProps = new LinkedHashMap<>();
        colProps.put("name", prop("name", "string", ""));
        colProps.put("type", prop("type", "string", ""));
        colProps.put("comment", prop("comment", "string", ""));
        columnItem.put("properties", colProps);
        columnItem.put("required", Arrays.asList("name", "type"));

        Map<String, Object> columnsArr = new LinkedHashMap<>();
        columnsArr.put("type", "array");
        columnsArr.put("items", columnItem);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("target_table", prop("target_table", "string", "Name of the output target table"));
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
        stepProps.put("step_order", prop("step_order", "integer", ""));
        stepProps.put("step_name", prop("step_name", "string", ""));
        stepProps.put("description", prop("description", "string", ""));
        stepProps.put("is_temp_table", prop("is_temp_table", "boolean", ""));
        stepProps.put("output_table", prop("output_table", "string", ""));
        stepItem.put("properties", stepProps);
        stepItem.put("required", Arrays.asList("step_order","step_name","description",
            "is_temp_table","output_table"));

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
        props.put("step_order", prop("step_order", "integer", ""));
        props.put("step_name", prop("step_name", "string", ""));
        props.put("is_temp_table", prop("is_temp_table", "boolean", ""));
        props.put("sql", prop("sql", "string", "Complete GaussDB SQL for this step"));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "object");
        p.put("properties", props);
        p.put("required", Arrays.asList("step_order","step_name","is_temp_table","sql"));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add AgentDomainService (tool-use loop, 5 tools, state machine)"
```

---

## Task 17：EtlDomainService + AgentAppService + /stream 端点

**Files:**
- Create: `domain/service/EtlDomainService.java`
- Create: `application/service/AgentAppService.java`
- Modify: `interfaces/rest/ConversationController.java`（追加 `/stream` 方法）

- [ ] **Step 1: 创建 `EtlDomainService.java`**

```java
package com.ddh.agent.domain.service;

import com.ddh.agent.domain.model.etl.*;
import com.ddh.agent.infrastructure.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class EtlDomainService {

    @Autowired private EtlRepository etlRepository;
    @Autowired private AppProperties appProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9_\\u4e00-\\u9fff]");

    public String writeSqlFile(Long projectId, int stepOrder,
                               String stepName, String sql) {
        Path dir = projectDir(projectId);
        String filename = "step" + stepOrder + "_" + safeName(stepName) + ".sql";
        Path path = dir.resolve(filename);
        try {
            Files.write(path, sql.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write SQL file: " + path, e);
        }
        return path.toString();
    }

    public String writePlanMd(Long projectId, String targetTable,
                              String requirement, List<Map<String, Object>> steps) {
        Path dir = projectDir(projectId);
        StringBuilder sb = new StringBuilder();
        sb.append("# ETL 执行计划\n\n## 需求描述\n\n").append(requirement)
          .append("\n\n## 目标表\n\n`").append(targetTable).append("`\n\n## ETL 步骤\n\n");
        for (Map<String, Object> step : steps) {
            boolean isTemp = Boolean.TRUE.equals(step.get("is_temp_table"));
            sb.append("### Step ").append(step.get("step_order"))
              .append(": ").append(step.get("step_name"))
              .append(isTemp ? "（临时表）" : "").append("\n\n")
              .append(step.getOrDefault("description", "")).append("\n\n")
              .append("输出表：`").append(step.getOrDefault("output_table", "")).append("`\n\n");
        }
        Path path = dir.resolve("plan.md");
        try {
            Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write plan.md", e);
        }
        return path.toString();
    }

    public EtlJob createJob(Long projectId, String targetTable,
                            List<Map<String, Object>> targetSchema, String planMdPath) {
        EtlJob job = new EtlJob();
        job.setProjectId(projectId);
        job.setTargetTable(targetTable);
        try { job.setTargetSchema(mapper.writeValueAsString(targetSchema)); }
        catch (Exception e) { job.setTargetSchema("[]"); }
        job.setPlanMdPath(planMdPath);
        job.setCreatedAt(LocalDateTime.now());
        return etlRepository.saveJob(job);
    }

    public EtlStep createStep(Long jobId, int stepOrder, String stepName,
                              boolean isTempTable, String sqlFilePath) {
        EtlStep step = new EtlStep();
        step.setJobId(jobId);
        step.setStepOrder(stepOrder);
        step.setStepName(stepName);
        step.setIsTempTable(isTempTable ? 1 : 0);
        step.setSqlFilePath(sqlFilePath);
        return etlRepository.saveStep(step);
    }

    private Path projectDir(Long projectId) {
        Path dir = Paths.get(appProperties.getProjectsDir()).resolve(projectId.toString());
        try { Files.createDirectories(dir); }
        catch (IOException e) { throw new RuntimeException("Cannot create project dir", e); }
        return dir;
    }

    private String safeName(String s) {
        return UNSAFE.matcher(s).replaceAll("_").substring(0, Math.min(s.length(), 64));
    }
}
```

- [ ] **Step 2: 创建 `AgentAppService.java`**

```java
package com.ddh.agent.application.service;

import com.ddh.agent.domain.model.conversation.*;
import com.ddh.agent.domain.service.AgentDomainService;
import com.ddh.agent.domain.service.EtlDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
public class AgentAppService {

    @Autowired private AgentDomainService agentDomainService;
    @Autowired private EtlDomainService etlDomainService;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired @Qualifier("agentExecutor") private ExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();

    public SseEmitter stream(Long conversationId) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                List<Map<String, Object>> generatedSteps = agentDomainService.run(
                    conversationId, event -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .data(mapper.writeValueAsString(event)));
                        } catch (Exception e) {
                            // client disconnected — best effort
                        }
                    });

                // state=4 完成后：持久化 EtlJob / EtlStep
                if (!generatedSteps.isEmpty()) {
                    persistEtlResult(conversationId, generatedSteps, emitter);
                }

                emitter.send(SseEmitter.event()
                    .data(mapper.writeValueAsString(map("type", "stream_end"))));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .data(mapper.writeValueAsString(
                            map("type", "error", "message", e.getMessage()))));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @SuppressWarnings("unchecked")
    private void persistEtlResult(Long conversationId,
                                   List<Map<String, Object>> generatedSteps,
                                   SseEmitter emitter) throws Exception {
        Conversation conv = conversationRepository.findById(conversationId)
            .orElseThrow();

        // 从消息历史中提取目标表名和 schema
        String targetTable = "target_table";
        List<Map<String, Object>> targetSchema = Collections.emptyList();
        String firstUser = "";
        Pattern p = Pattern.compile("目标表：(.+?)，字段：(.+)$");
        for (Message msg : conversationRepository.findMessagesByConversationId(conversationId)) {
            if ("user".equals(msg.getRole())) {
                if (firstUser.isEmpty()) firstUser = msg.getContent();
                Matcher m = p.matcher(msg.getContent());
                if (m.find()) {
                    targetTable = m.group(1).trim();
                    try {
                        targetSchema = mapper.readValue(m.group(2).trim(), List.class);
                    } catch (Exception ignored) {}
                }
            }
        }

        List<Map<String, Object>> stepsForPlan = generatedSteps.stream()
            .sorted(Comparator.comparingInt(s -> toInt(s.get("step_order"))))
            .map(s -> {
                Map<String, Object> sm = new HashMap<>();
                sm.put("step_order", s.get("step_order"));
                sm.put("step_name", s.get("step_name"));
                sm.put("description", s.get("step_name"));
                sm.put("is_temp_table", s.get("is_temp_table"));
                sm.put("output_table", targetTable);
                return sm;
            }).collect(Collectors.toList());

        String planPath = etlDomainService.writePlanMd(
            conv.getProjectId(), targetTable,
            firstUser.length() > 500 ? firstUser.substring(0, 500) : firstUser,
            stepsForPlan);

        EtlJob job = etlDomainService.createJob(
            conv.getProjectId(), targetTable, targetSchema, planPath);

        for (Map<String, Object> step : generatedSteps.stream()
                .sorted(Comparator.comparingInt(s -> toInt(s.get("step_order"))))
                .collect(Collectors.toList())) {
            String filePath = etlDomainService.writeSqlFile(
                conv.getProjectId(),
                toInt(step.get("step_order")),
                (String) step.get("step_name"),
                (String) step.get("sql"));
            etlDomainService.createStep(
                job.getId(),
                toInt(step.get("step_order")),
                (String) step.get("step_name"),
                Boolean.TRUE.equals(step.get("is_temp_table")),
                filePath);
        }

        conv.setState(5);
        conversationRepository.save(conv);

        emitter.send(SseEmitter.event()
            .data(mapper.writeValueAsString(map("type", "done", "job_id", job.getId()))));
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
```

- [ ] **Step 3: 在 `ConversationController.java` 追加 `/stream` 端点**

在 `ConversationController` 类末尾添加（注入 `AgentAppService`）：

```java
// 在类顶部新增注入
@Autowired private com.ddh.agent.application.service.AgentAppService agentAppService;

// 在类末尾新增方法
@GetMapping(value = "/conversations/{convId}/stream",
            produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable Long convId, Authentication auth) {
    conversationAppService.requireConversation(convId);
    return agentAppService.stream(convId);
}
```

- [ ] **Step 4: 编译确认**

```powershell
mvn compile -pl backend-java -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 启动并手工验证 SSE 端点可达**

```powershell
# 启动（需本地 MySQL + LLM API key 配置）
mvn spring-boot:run -pl backend-java
# 另开终端
curl -N -H "Authorization: Bearer <token>" http://localhost:8000/api/conversations/1/stream
```
Expected: 返回 `text/event-stream` 响应，不报 404

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/
git commit -m "feat(java): add EtlDomainService, AgentAppService with SSE stream and ETL persistence"
```

---

**Plan C 完成。** AI 对话完整流程（分析需求 → 提议 Schema → 生成 ETL 步骤 → 生成 SQL → 持久化 Job）已可用。继续执行 [Plan D — Job / Admin / 集成验收](2026-06-13-java-plan-d-finalize.md)。
