package com.ddh.agent.infrastructure.llm;

import com.ddh.agent.domain.service.LlmPort;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** 可运行时替换委托的 LlmPort 包装器，支持 Admin 热切换 provider。 */
public class DynamicLlmPort implements LlmPort {

    private final AtomicReference<LlmPort> delegate;

    public DynamicLlmPort(LlmPort initial) {
        this.delegate = new AtomicReference<>(initial);
    }

    public void setDelegate(LlmPort newDelegate) {
        this.delegate.set(newDelegate);
    }

    @Override
    public LlmResponse chatWithTools(List<Map<String, Object>> messages,
                                     List<Map<String, Object>> tools,
                                     String systemPrompt) {
        return delegate.get().chatWithTools(messages, tools, systemPrompt);
    }
}