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

            ArrayNode msgs = body.putArray("messages");
            for (Map<String, Object> m : messages) {
                String role = (String) m.get("role");
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
                    if (m.containsKey("tool_calls")) {
                        msg.set("content", buildAssistantContent(
                            contentObj instanceof String ? (String) contentObj : "",
                            (List<?>) m.get("tool_calls")));
                    } else if (contentObj instanceof String) {
                        msg.put("content", (String) contentObj);
                    } else if (contentObj != null) {
                        msg.set("content", mapper.valueToTree(contentObj));
                    } else {
                        msg.put("content", "");
                    }
                    msgs.add(msg);
                }
            }

            if (tools != null && !tools.isEmpty()) {
                body.set("tools", buildTools(tools));
            }

            Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
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

    /** 将通用工具定义 {name, description, parameters} 映射为 Anthropic 的 {name, description, input_schema}。 */
    private ArrayNode buildTools(List<Map<String, Object>> tools) {
        ArrayNode arr = mapper.createArrayNode();
        for (Map<String, Object> t : tools) {
            ObjectNode tool = arr.addObject();
            tool.put("name", (String) t.get("name"));
            tool.put("description", (String) t.get("description"));
            tool.set("input_schema", mapper.valueToTree(t.get("parameters")));
        }
        return arr;
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
        // Anthropic uses "tool_use" stop_reason; normalize so the agent loop continues.
        return new LlmResponse(text, stopReason, toolCalls);
    }
}