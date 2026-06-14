package com.ddh.agent.application.service;

import com.ddh.agent.domain.model.conversation.*;
import com.ddh.agent.domain.model.etl.EtlJob;
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

    private static final Pattern SCHEMA_PATTERN =
        Pattern.compile("目标表：(.+?)，字段：(.+)$");

    public SseEmitter stream(Long conversationId) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                List<Map<String, Object>> generatedSteps = agentDomainService.run(
                    conversationId, event -> send(emitter, event));

                if (!generatedSteps.isEmpty()) {
                    persistEtlResult(conversationId, generatedSteps, emitter);
                }

                send(emitter, map("type", "stream_end"));
                send(emitter, map("type", "end"));
                emitter.complete();
            } catch (Exception e) {
                send(emitter, map("type", "error", "message", e.getMessage()));
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void send(SseEmitter emitter, Map<String, Object> event) {
        try {
            emitter.send(SseEmitter.event().data(mapper.writeValueAsString(event)));
        } catch (Exception ignored) {
            // client disconnected — best effort
        }
    }

    @SuppressWarnings("unchecked")
    private void persistEtlResult(Long conversationId,
                                  List<Map<String, Object>> generatedSteps,
                                  SseEmitter emitter) {
        Conversation conv = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // 从消息历史中提取目标表名、schema 和首条用户需求
        String targetTable = "target_table";
        List<Map<String, Object>> targetSchema = Collections.emptyList();
        String firstUser = "ETL requirement";
        boolean firstUserSet = false;
        for (Message msg : conversationRepository.findMessagesByConversationId(conversationId)) {
            if ("user".equals(msg.getRole())) {
                if (!firstUserSet) {
                    firstUser = msg.getContent();
                    firstUserSet = true;
                }
                if (msg.getContent() != null && msg.getContent().contains("目标表结构已确认")) {
                    Matcher m = SCHEMA_PATTERN.matcher(msg.getContent());
                    if (m.find()) {
                        targetTable = m.group(1).trim();
                        try {
                            targetSchema = mapper.readValue(m.group(2).trim(), List.class);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        final String finalTarget = targetTable;
        List<Map<String, Object>> sorted = generatedSteps.stream()
            .sorted(Comparator.comparingInt(s -> toInt(s.get("step_order"))))
            .collect(Collectors.toList());

        List<Map<String, Object>> stepsForPlan = sorted.stream()
            .map(s -> {
                Map<String, Object> sm = new HashMap<>();
                sm.put("step_order", s.get("step_order"));
                sm.put("step_name", s.get("step_name"));
                sm.put("description", s.get("step_name"));
                sm.put("is_temp_table", s.get("is_temp_table"));
                sm.put("output_table", finalTarget);
                return sm;
            }).collect(Collectors.toList());

        String requirement = firstUser.length() > 500 ? firstUser.substring(0, 500) : firstUser;
        String planPath = etlDomainService.writePlanMd(
            conv.getProjectId(), targetTable, requirement, stepsForPlan);

        EtlJob job = etlDomainService.createJob(
            conv.getProjectId(), conv.getId(), targetTable, targetSchema, planPath);

        for (Map<String, Object> step : sorted) {
            etlDomainService.createStep(
                job.getId(),
                toInt(step.get("step_order")),
                (String) step.get("step_name"),
                Boolean.TRUE.equals(step.get("is_temp_table")),
                (String) step.get("file_path"));
        }

        conv.setState(5);
        conversationRepository.save(conv);

        send(emitter, map("type", "done", "job_id", job.getId()));
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
