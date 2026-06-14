package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort.LlmResponse;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiStreamParseTest {

    private DeepSeekAdapter adapter() {
        return new DeepSeekAdapter(new OkHttpClient(), "k", "deepseek-chat");
    }

    @Test
    void accumulatesContentDeltasAndEmitsEach() {
        List<String> lines = Arrays.asList(
            "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"，世界\"}}]}",
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
            "data: [DONE]");
        List<String> deltas = new ArrayList<>();
        LlmResponse r = adapter().consumeStream(lines, deltas::add);
        assertEquals(Arrays.asList("你好", "，世界"), deltas);
        assertEquals("你好，世界", r.text);
        assertEquals("end_turn", r.stopReason);
        assertTrue(r.toolCalls.isEmpty());
    }

    @Test
    void accumulatesToolCallArgumentFragments() {
        List<String> lines = Arrays.asList(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"get_table_schema\",\"arguments\":\"{\\\"table\"}}]}}]}",
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"_id\\\":5}\"}}]}}]}",
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
            "data: [DONE]");
        LlmResponse r = adapter().consumeStream(lines, s -> {});
        assertEquals("tool_use", r.stopReason);
        assertEquals(1, r.toolCalls.size());
        assertEquals("get_table_schema", r.toolCalls.get(0).name);
        assertEquals(5, ((Number) r.toolCalls.get(0).input.get("table_id")).intValue());
    }
}
