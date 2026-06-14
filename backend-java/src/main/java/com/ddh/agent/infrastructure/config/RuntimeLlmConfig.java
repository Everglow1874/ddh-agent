package com.ddh.agent.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 运行时可变的 LLM 配置，volatile 保证线程可见性，供 Admin 热切换使用。
 */
@Component
public class RuntimeLlmConfig {

    private volatile String provider;
    private volatile String claudeApiKey;
    private volatile String claudeModel;
    private volatile String deepseekApiKey;
    private volatile String deepseekModel;
    private volatile String qwenApiKey;
    private volatile String qwenModel;

    @Value("${llm.provider}")
    public void setProvider(String v) {
        this.provider = v;
    }

    @Value("${llm.claude.api-key:}")
    public void setClaudeApiKey(String v) {
        this.claudeApiKey = v;
    }

    @Value("${llm.claude.model:claude-sonnet-4-6}")
    public void setClaudeModel(String v) {
        this.claudeModel = v;
    }

    @Value("${llm.deepseek.api-key:}")
    public void setDeepseekApiKey(String v) {
        this.deepseekApiKey = v;
    }

    @Value("${llm.deepseek.model:deepseek-chat}")
    public void setDeepseekModel(String v) {
        this.deepseekModel = v;
    }

    @Value("${llm.qwen.api-key:}")
    public void setQwenApiKey(String v) {
        this.qwenApiKey = v;
    }

    @Value("${llm.qwen.model:qwen-max}")
    public void setQwenModel(String v) {
        this.qwenModel = v;
    }

    public String getProvider() {
        return provider;
    }

    public String getClaudeApiKey() {
        return claudeApiKey;
    }

    public String getClaudeModel() {
        return claudeModel;
    }

    public String getDeepseekApiKey() {
        return deepseekApiKey;
    }

    public String getDeepseekModel() {
        return deepseekModel;
    }

    public String getQwenApiKey() {
        return qwenApiKey;
    }

    public String getQwenModel() {
        return qwenModel;
    }

    /**
     * 当前 provider 对应的模型名。
     */
    public String currentModel() {
        if ("deepseek".equals(provider)) return deepseekModel;
        if ("qwen".equals(provider)) return qwenModel;
        return claudeModel;
    }
}