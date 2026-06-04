from app.services.llm.openai_compatible import (
    OpenAICompatibleProvider,
    to_openai_messages,
    to_openai_tools,
)

# Backwards-compatible aliases — existing tests import these from this module.
_to_openai_messages = to_openai_messages
_to_openai_tools = to_openai_tools


class QwenProvider(OpenAICompatibleProvider):
    def __init__(self, api_key: str, model: str):
        super().__init__(
            api_key=api_key,
            model=model,
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
        )
