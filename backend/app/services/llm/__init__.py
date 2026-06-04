from app.services.llm.base import BaseLLMProvider, LLMResponse, ToolCall
from app.services.llm.claude_provider import ClaudeProvider
from app.services.llm.qwen_provider import QwenProvider
from app.services.llm.deepseek_provider import DeepSeekProvider
from app.config import settings


def get_llm_provider() -> BaseLLMProvider:
    provider = settings.llm_provider
    if provider == "claude":
        return ClaudeProvider(api_key=settings.claude_api_key, model=settings.claude_model)
    if provider == "qwen":
        return QwenProvider(api_key=settings.qwen_api_key, model=settings.qwen_model)
    if provider == "deepseek":
        return DeepSeekProvider(api_key=settings.deepseek_api_key, model=settings.deepseek_model)
    raise ValueError(f"Unknown LLM provider: {provider!r}. Must be 'claude', 'qwen', or 'deepseek'.")


__all__ = [
    "BaseLLMProvider",
    "LLMResponse",
    "ToolCall",
    "get_llm_provider",
    "ClaudeProvider",
    "QwenProvider",
    "DeepSeekProvider",
]
