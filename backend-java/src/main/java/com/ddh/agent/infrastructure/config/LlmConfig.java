package com.ddh.agent.infrastructure.config;

import com.ddh.agent.domain.service.LlmPort;
import com.ddh.agent.infrastructure.llm.*;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Autowired private OkHttpClient okHttpClient;
    @Autowired private RuntimeLlmConfig runtimeLlmConfig;

    /** 包装为 DynamicLlmPort，支持 Admin 运行时热切换 provider。 */
    @Bean
    public LlmPort llmPort() {
        return new DynamicLlmPort(buildAdapter(okHttpClient, runtimeLlmConfig));
    }

    /** 根据当前运行时配置构建对应 provider 的适配器。 */
    public static LlmPort buildAdapter(OkHttpClient client, RuntimeLlmConfig cfg) {
        String provider = cfg.getProvider();
        if ("deepseek".equals(provider)) {
            return new DeepSeekAdapter(client, cfg.getDeepseekApiKey(), cfg.getDeepseekModel());
        }
        if ("qwen".equals(provider)) {
            return new QwenAdapter(client, cfg.getQwenApiKey(), cfg.getQwenModel());
        }
        return new ClaudeAdapter(client, cfg.getClaudeApiKey(), cfg.getClaudeModel());
    }
}
