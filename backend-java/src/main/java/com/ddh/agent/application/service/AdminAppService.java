package com.ddh.agent.application.service;

import com.ddh.agent.application.dto.AdminConfigDto;
import com.ddh.agent.domain.service.LlmPort;
import com.ddh.agent.infrastructure.config.LlmConfig;
import com.ddh.agent.infrastructure.config.RuntimeLlmConfig;
import com.ddh.agent.infrastructure.llm.DynamicLlmPort;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.List;

@Service
public class AdminAppService {

    private static final List<String> VALID_PROVIDERS = Arrays.asList("claude", "qwen", "deepseek");

    @Autowired private RuntimeLlmConfig runtimeLlmConfig;
    @Autowired private OkHttpClient okHttpClient;
    @Autowired private LlmPort llmPort;

    public AdminConfigDto getConfig() {
        return new AdminConfigDto(runtimeLlmConfig.getProvider(), runtimeLlmConfig.currentModel());
    }

    public AdminConfigDto updateConfig(AdminConfigDto req) {
        if (req.provider == null || !VALID_PROVIDERS.contains(req.provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "provider must be one of " + VALID_PROVIDERS);
        }
        runtimeLlmConfig.setProvider(req.provider);
        if (req.model != null) {
            if ("claude".equals(req.provider)) runtimeLlmConfig.setClaudeModel(req.model);
            else if ("qwen".equals(req.provider)) runtimeLlmConfig.setQwenModel(req.model);
            else runtimeLlmConfig.setDeepseekModel(req.model);
        }

        // 热切换底层适配器
        if (llmPort instanceof DynamicLlmPort) {
            ((DynamicLlmPort) llmPort).setDelegate(
                LlmConfig.buildAdapter(okHttpClient, runtimeLlmConfig));
        }

        return new AdminConfigDto(runtimeLlmConfig.getProvider(), runtimeLlmConfig.currentModel());
    }
}
