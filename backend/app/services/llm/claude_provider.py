import anthropic
from app.services.llm.base import BaseLLMProvider, LLMResponse, ToolCall


def _to_anthropic_messages(messages: list[dict]) -> list[dict]:
    result = []
    for msg in messages:
        role = msg["role"]
        if role == "user":
            result.append({"role": "user", "content": msg["content"]})
        elif role == "assistant":
            result.append({"role": "assistant", "content": [{"type": "text", "text": msg["content"]}]})
        elif role == "assistant_tool_use":
            blocks = [
                {"type": "tool_use", "id": tc["id"], "name": tc["name"], "input": tc["input"]}
                for tc in msg["tool_calls"]
            ]
            result.append({"role": "assistant", "content": blocks})
        elif role == "tool_result":
            # Anthropic groups tool results inside a user message
            if result and result[-1]["role"] == "user" and isinstance(result[-1]["content"], list):
                result[-1]["content"].append({
                    "type": "tool_result",
                    "tool_use_id": msg["tool_call_id"],
                    "content": msg["content"],
                })
            else:
                result.append({"role": "user", "content": [{
                    "type": "tool_result",
                    "tool_use_id": msg["tool_call_id"],
                    "content": msg["content"],
                }]})
    return result


def _to_anthropic_tools(tools: list[dict]) -> list[dict]:
    return [
        {
            "name": t["name"],
            "description": t["description"],
            "input_schema": t["parameters"],
        }
        for t in tools
    ]


class ClaudeProvider(BaseLLMProvider):
    def __init__(self, api_key: str, model: str):
        self.client = anthropic.Anthropic(api_key=api_key)
        self.model = model

    def chat_with_tools(
        self,
        messages: list[dict],
        tools: list[dict],
        system: str = "",
    ) -> LLMResponse:
        response = self.client.messages.create(
            model=self.model,
            max_tokens=4096,
            system=system,
            tools=_to_anthropic_tools(tools),
            messages=_to_anthropic_messages(messages),
        )
        text = ""
        tool_calls = []
        for block in response.content:
            if block.type == "text":
                text += block.text
            elif block.type == "tool_use":
                tool_calls.append(ToolCall(id=block.id, name=block.name, input=block.input))
        stop = "tool_use" if response.stop_reason == "tool_use" else "end_turn"
        return LLMResponse(text=text, stop_reason=stop, tool_calls=tool_calls)
