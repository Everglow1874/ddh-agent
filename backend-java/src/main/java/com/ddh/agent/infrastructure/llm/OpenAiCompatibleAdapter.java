package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import okhttp3.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

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
            ObjectNode body = buildRequestBody(messages, tools, systemPrompt);

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

    protected ObjectNode buildRequestBody(List<Map<String, Object>> messages,
                                          List<Map<String, Object>> tools,
                                          String systemPrompt) {
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
        return body;
    }

    @Override
    public LlmResponse chatWithToolsStream(List<Map<String, Object>> messages,
                                           List<Map<String, Object>> tools,
                                           String systemPrompt,
                                           Consumer<String> onTextDelta) {
        try {
            ObjectNode body = buildRequestBody(messages, tools, systemPrompt);
            body.put("stream", true);
            Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String rb = response.body() != null ? response.body().string() : "";
                    throw new RuntimeException(apiUrl + " error " + response.code() + ": " + rb);
                }
                okhttp3.ResponseBody respBody = response.body();
                if (respBody == null) {
                    throw new RuntimeException(apiUrl + " empty streaming body");
                }
                okio.BufferedSource source = respBody.source();
                StreamAccumulator acc = new StreamAccumulator();
                String line;
                while ((line = source.readUtf8Line()) != null) {
                    acc.processLine(line, onTextDelta);
                }
                return acc.build();
            }
        } catch (IOException e) {
            throw new RuntimeException("LLM streaming call failed: " + apiUrl, e);
        }
    }

    /** 解析 OpenAI 兼容流式 data: 行序列（包级可见，便于单测）。逐行处理，delta 即时回调。 */
    LlmResponse consumeStream(List<String> lines, Consumer<String> onTextDelta) {
        StreamAccumulator acc = new StreamAccumulator();
        for (String raw : lines) {
            acc.processLine(raw, onTextDelta);
        }
        return acc.build();
    }

    /** 累积 OpenAI 兼容流式解析状态；逐行喂入，结束时 build。 */
    private final class StreamAccumulator {
        private final StringBuilder fullText = new StringBuilder();
        private final java.util.Map<Integer, ToolCallBuilder> toolBuilders = new java.util.LinkedHashMap<>();
        private String finishReason = "stop";

        void processLine(String raw, Consumer<String> onTextDelta) {
            if (raw == null || !raw.startsWith("data:")) return;
            String data = raw.substring("data:".length()).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) return;
            JsonNode root;
            try { root = mapper.readTree(data); } catch (Exception e) { return; }
            JsonNode choice = root.path("choices").path(0);
            JsonNode delta = choice.path("delta");
            JsonNode contentNode = delta.path("content");
            if (contentNode.isTextual()) {
                String piece = contentNode.asText();
                if (!piece.isEmpty()) {
                    fullText.append(piece);
                    onTextDelta.accept(piece);
                }
            }
            for (JsonNode tc : delta.path("tool_calls")) {
                int idx = tc.path("index").asInt(0);
                ToolCallBuilder b = toolBuilders.computeIfAbsent(idx, k -> new ToolCallBuilder());
                if (tc.hasNonNull("id")) b.id = tc.path("id").asText();
                JsonNode fn = tc.path("function");
                if (fn.hasNonNull("name")) b.name = fn.path("name").asText();
                if (fn.hasNonNull("arguments")) b.args.append(fn.path("arguments").asText());
            }
            String fr = choice.path("finish_reason").asText("");
            if (!fr.isEmpty() && !"null".equals(fr)) finishReason = fr;
        }

        @SuppressWarnings("unchecked")
        LlmResponse build() {
            String stopReason = "tool_calls".equals(finishReason) ? "tool_use" : "end_turn";
            List<ToolCall> toolCalls = new ArrayList<>();
            for (ToolCallBuilder b : toolBuilders.values()) {
                Map<String, Object> input;
                try {
                    input = b.args.length() > 0
                        ? mapper.readValue(b.args.toString(), Map.class) : new HashMap<>();
                } catch (Exception e) { input = new HashMap<>(); }
                toolCalls.add(new ToolCall(b.id, b.name, input));
            }
            String text = fullText.length() > 0 ? fullText.toString() : null;
            return new LlmResponse(text, stopReason, toolCalls);
        }
    }

    private static class ToolCallBuilder {
        String id; String name; StringBuilder args = new StringBuilder();
    }
}
