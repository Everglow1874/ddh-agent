from abc import ABC, abstractmethod
from dataclasses import dataclass, field


@dataclass
class ToolCall:
    id: str
    name: str
    input: dict


@dataclass
class LLMResponse:
    text: str
    stop_reason: str  # "end_turn" | "tool_use"
    tool_calls: list[ToolCall] = field(default_factory=list)

    def to_assistant_message(self) -> dict:
        if self.stop_reason == "tool_use":
            return {
                "role": "assistant_tool_use",
                "tool_calls": [
                    {"id": tc.id, "name": tc.name, "input": tc.input}
                    for tc in self.tool_calls
                ],
            }
        return {"role": "assistant", "content": self.text}


class BaseLLMProvider(ABC):
    """Canonical message format:
    - {"role": "user", "content": "text"}
    - {"role": "assistant", "content": "text"}
    - {"role": "assistant_tool_use", "tool_calls": [{"id": str, "name": str, "input": dict}]}
    - {"role": "tool_result", "tool_call_id": str, "content": "json string"}

    Canonical tool format:
    {"name": str, "description": str, "parameters": JSON-Schema-object}
    """

    @abstractmethod
    def chat_with_tools(
        self,
        messages: list[dict],
        tools: list[dict],
        system: str = "",
    ) -> LLMResponse:
        pass
