import json
from openai import OpenAI
from app.services.llm.base import BaseLLMProvider, LLMResponse, ToolCall


def to_openai_messages(messages: list[dict], system: str) -> list[dict]:
    result = []
    if system:
        result.append({"role": "system", "content": system})
    for msg in messages:
        role = msg["role"]
        if role == "user":
            result.append({"role": "user", "content": msg["content"]})
        elif role == "assistant":
            result.append({"role": "assistant", "content": msg["content"]})
        elif role == "assistant_tool_use":
            result.append({
                "role": "assistant",
                "content": None,
                "tool_calls": [
                    {
                        "id": tc["id"],
                        "type": "function",
                        "function": {"name": tc["name"], "arguments": json.dumps(tc["input"])},
                    }
                    for tc in msg["tool_calls"]
                ],
            })
        elif role == "tool_result":
            result.append({
                "role": "tool",
                "tool_call_id": msg["tool_call_id"],
                "content": msg["content"],
            })
    return result


def to_openai_tools(tools: list[dict]) -> list[dict]:
    return [
        {"type": "function", "function": {"name": t["name"], "description": t["description"], "parameters": t["parameters"]}}
        for t in tools
    ]


class OpenAICompatibleProvider(BaseLLMProvider):
    """Base class for providers exposing an OpenAI-compatible chat completions
    API (Qwen via DashScope, DeepSeek, etc). Subclasses only supply base_url."""

    def __init__(self, api_key: str, model: str, base_url: str):
        self.client = OpenAI(api_key=api_key, base_url=base_url)
        self.model = model

    def chat_with_tools(
        self,
        messages: list[dict],
        tools: list[dict],
        system: str = "",
    ) -> LLMResponse:
        response = self.client.chat.completions.create(
            model=self.model,
            messages=to_openai_messages(messages, system),
            tools=to_openai_tools(tools),
            tool_choice="auto",
        )
        choice = response.choices[0]
        msg = choice.message
        text = msg.content or ""
        tool_calls = []
        if msg.tool_calls:
            for tc in msg.tool_calls:
                tool_calls.append(ToolCall(
                    id=tc.id,
                    name=tc.function.name,
                    input=json.loads(tc.function.arguments),
                ))
        stop = "tool_use" if choice.finish_reason == "tool_calls" else "end_turn"
        return LLMResponse(text=text, stop_reason=stop, tool_calls=tool_calls)
