package com.ddh.agent.domain.service;

import java.util.List;
import java.util.Map;

public interface LlmPort {

    LlmResponse chatWithTools(List<Map<String, Object>> messages,
                              List<Map<String, Object>> tools,
                              String systemPrompt);

    class LlmResponse {
        public final String text;
        public final String stopReason;
        public final List<ToolCall> toolCalls;

        public LlmResponse(String text, String stopReason, List<ToolCall> toolCalls) {
            this.text = text;
            this.stopReason = stopReason;
            this.toolCalls = toolCalls;
        }

        public Map<String, Object> toAssistantMessage() {
            java.util.Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("role", "assistant");
            msg.put("content", text != null ? text : "");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                msg.put("tool_calls", toolCalls);
            }
            return msg;
        }
    }

    class ToolCall {
        public final String id;
        public final String name;
        public final Map<String, Object> input;

        public ToolCall(String id, String name, Map<String, Object> input) {
            this.id = id;
            this.name = name;
            this.input = input;
        }
    }
}
