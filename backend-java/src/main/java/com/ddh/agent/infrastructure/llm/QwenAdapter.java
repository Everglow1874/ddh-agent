package com.ddh.agent.infrastructure.llm;

import okhttp3.OkHttpClient;

/** Qwen via DashScope OpenAI-compatible endpoint */
public class QwenAdapter extends OpenAiCompatibleAdapter {
    public QwenAdapter(OkHttpClient client, String apiKey, String model) {
        super(client, apiKey, model,
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
    }
}