import pytest
from unittest.mock import MagicMock, patch
from app.services.llm.base import BaseLLMProvider, LLMResponse, ToolCall
from app.services.llm import get_llm_provider


def test_llm_response_dataclass():
    tc = ToolCall(id="c1", name="my_tool", input={"x": 1})
    resp = LLMResponse(text="hello", stop_reason="end_turn", tool_calls=[])
    assert resp.text == "hello"
    assert resp.stop_reason == "end_turn"
    assert tc.name == "my_tool"


def test_llm_response_to_assistant_message_text():
    resp = LLMResponse(text="answer", stop_reason="end_turn", tool_calls=[])
    msg = resp.to_assistant_message()
    assert msg["role"] == "assistant"
    assert msg["content"] == "answer"


def test_llm_response_to_assistant_message_tool_use():
    tc = ToolCall(id="abc", name="get_table_schema", input={"table_id": 5})
    resp = LLMResponse(text="", stop_reason="tool_use", tool_calls=[tc])
    msg = resp.to_assistant_message()
    assert msg["role"] == "assistant_tool_use"
    assert msg["tool_calls"][0]["name"] == "get_table_schema"


def test_get_llm_provider_returns_provider():
    with patch("app.services.llm.settings") as mock_settings:
        mock_settings.llm_provider = "claude"
        mock_settings.claude_api_key = "test-key"
        mock_settings.claude_model = "claude-sonnet-4-6"
        provider = get_llm_provider()
        assert isinstance(provider, BaseLLMProvider)


def test_get_llm_provider_unknown_raises():
    with patch("app.services.llm.settings") as mock_settings:
        mock_settings.llm_provider = "unknown_provider"
        with pytest.raises(ValueError, match="unknown_provider"):
            get_llm_provider()
