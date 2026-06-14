package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import okhttp3.*;
import java.io.IOException;
import java.util.*;

/** OpenAI 兼容的 /v1/chat/completions 适配器基类（DeepSeek / Qwen 共用）。 */
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
                .url(apiUrl)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .build();

            try (Response response = client.newCall(request).execute()) {
                String rb = response.body() != null ? response.body().string() : "";
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
        String text = msgNode.path("content").isNull() ? null
            : msgNode.path("content").asText(null);
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