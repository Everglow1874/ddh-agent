package com.ddh.agent.infrastructure.llm;

import okhttp3.OkHttpClient;

public class DeepSeekAdapter extends OpenAiCompatibleAdapter {
    public DeepSeekAdapter(OkHttpClient client, String apiKey, String model) {
        super(client, apiKey, model, "https://api.deepseek.com/v1/chat/completions");
    }
}