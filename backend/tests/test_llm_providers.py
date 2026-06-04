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


from app.services.llm.claude_provider import _to_anthropic_messages, _to_anthropic_tools
from app.services.llm.qwen_provider import _to_openai_messages, _to_openai_tools
import json


def test_to_anthropic_messages_user():
    msgs = [{"role": "user", "content": "hello"}]
    result = _to_anthropic_messages(msgs)
    assert result[0] == {"role": "user", "content": "hello"}


def test_to_anthropic_messages_assistant():
    msgs = [{"role": "assistant", "content": "hi"}]
    result = _to_anthropic_messages(msgs)
    assert result[0]["role"] == "assistant"
    assert result[0]["content"][0]["type"] == "text"
    assert result[0]["content"][0]["text"] == "hi"


def test_to_anthropic_messages_tool_results_grouped():
    msgs = [
        {"role": "assistant_tool_use", "tool_calls": [{"id": "c1", "name": "tool_a", "input": {}}]},
        {"role": "tool_result", "tool_call_id": "c1", "content": '{"x": 1}'},
        {"role": "tool_result", "tool_call_id": "c2", "content": '{"y": 2}'},
    ]
    result = _to_anthropic_messages(msgs)
    # tool_results should be grouped into one user message
    assert result[-1]["role"] == "user"
    assert len(result[-1]["content"]) == 2
    assert result[-1]["content"][0]["type"] == "tool_result"
    assert result[-1]["content"][1]["type"] == "tool_result"


def test_to_anthropic_tools():
    tools = [{"name": "my_tool", "description": "does stuff", "parameters": {"type": "object", "properties": {}}}]
    result = _to_anthropic_tools(tools)
    assert result[0]["name"] == "my_tool"
    assert "input_schema" in result[0]
    assert "parameters" not in result[0]


def test_to_openai_messages_with_system():
    msgs = [{"role": "user", "content": "hi"}]
    result = _to_openai_messages(msgs, system="You are helpful.")
    assert result[0] == {"role": "system", "content": "You are helpful."}
    assert result[1] == {"role": "user", "content": "hi"}


def test_to_openai_messages_tool_use():
    msgs = [
        {"role": "assistant_tool_use", "tool_calls": [{"id": "t1", "name": "my_tool", "input": {"x": 1}}]},
        {"role": "tool_result", "tool_call_id": "t1", "content": '{"ok": true}'},
    ]
    result = _to_openai_messages(msgs, system="")
    assert result[0]["role"] == "assistant"
    assert result[0]["tool_calls"][0]["type"] == "function"
    assert result[0]["tool_calls"][0]["function"]["name"] == "my_tool"
    assert json.loads(result[0]["tool_calls"][0]["function"]["arguments"]) == {"x": 1}
    assert result[1]["role"] == "tool"
    assert result[1]["tool_call_id"] == "t1"


def test_to_openai_tools():
    tools = [{"name": "my_tool", "description": "does stuff", "parameters": {"type": "object"}}]
    result = _to_openai_tools(tools)
    assert result[0]["type"] == "function"
    assert result[0]["function"]["name"] == "my_tool"
    assert "parameters" in result[0]["function"]


from app.services.llm import DeepSeekProvider, QwenProvider
from app.services.llm.openai_compatible import OpenAICompatibleProvider


def test_get_llm_provider_deepseek():
    with patch("app.services.llm.settings") as mock_settings:
        mock_settings.llm_provider = "deepseek"
        mock_settings.deepseek_api_key = "test-key"
        mock_settings.deepseek_model = "deepseek-chat"
        provider = get_llm_provider()
        assert isinstance(provider, DeepSeekProvider)
        assert isinstance(provider, BaseLLMProvider)


def test_deepseek_provider_uses_deepseek_base_url():
    provider = DeepSeekProvider(api_key="k", model="deepseek-chat")
    assert str(provider.client.base_url).startswith("https://api.deepseek.com")
    assert provider.model == "deepseek-chat"


def test_qwen_provider_uses_dashscope_base_url():
    provider = QwenProvider(api_key="k", model="qwen-max")
    assert "dashscope" in str(provider.client.base_url)


def test_deepseek_and_qwen_share_openai_compatible_base():
    assert issubclass(DeepSeekProvider, OpenAICompatibleProvider)
    assert issubclass(QwenProvider, OpenAICompatibleProvider)
