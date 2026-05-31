import pytest
import asyncio
from unittest.mock import patch
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.database import Base
from app.models.conversation import Conversation, Message
from app.models.project import Project
from app.services.llm.base import BaseLLMProvider, LLMResponse, ToolCall
from app.services.agent_service import build_system_prompt, _build_message_history, _save_message


class MockLLMProvider(BaseLLMProvider):
    def __init__(self, responses: list[LLMResponse]):
        self._responses = iter(responses)

    def chat_with_tools(self, messages, tools, system="") -> LLMResponse:
        return next(self._responses)


@pytest.fixture(scope="function")
def db():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()
    Base.metadata.drop_all(engine)
    engine.dispose()


@pytest.fixture
def conversation(db):
    project = Project(name="Test Project", owner_id=1)
    db.add(project)
    db.flush()
    conv = Conversation(project_id=project.id, state=1)
    db.add(conv)
    db.commit()
    return conv


def test_build_system_prompt_state_1():
    prompt = build_system_prompt(1)
    assert "propose_schema" in prompt
    assert "GaussDB" in prompt


def test_build_system_prompt_state_4():
    prompt = build_system_prompt(4)
    assert "generate_sql" in prompt


def test_build_message_history_empty(db, conversation):
    history = _build_message_history(db, conversation.id)
    assert history == []


def test_build_message_history_with_messages(db, conversation):
    db.add(Message(conversation_id=conversation.id, role="user", content="hello"))
    db.add(Message(conversation_id=conversation.id, role="assistant", content="hi"))
    db.commit()
    history = _build_message_history(db, conversation.id)
    assert len(history) == 2
    assert history[0]["role"] == "user"
    assert history[0]["content"] == "hello"


def test_save_message(db, conversation):
    _save_message(db, conversation.id, "assistant", "response text")
    msgs = db.query(Message).filter(Message.conversation_id == conversation.id).all()
    assert len(msgs) == 1
    assert msgs[0].content == "response text"


@pytest.mark.asyncio
async def test_run_and_stream_simple_response(db, conversation):
    db.add(Message(conversation_id=conversation.id, role="user", content="analyze this"))
    db.commit()

    mock_response = LLMResponse(text="Here is my analysis.", stop_reason="end_turn", tool_calls=[])
    mock_provider = MockLLMProvider([mock_response])

    with patch("app.services.agent_service.get_llm_provider", return_value=mock_provider):
        events = []
        from app.services.agent_service import run_and_stream
        async for event in run_and_stream(conversation.id, lambda: db):
            events.append(event)

    types = [e["type"] for e in events]
    assert "token" in types
    assert "stream_end" in types


@pytest.mark.asyncio
async def test_run_and_stream_waiting_state(db):
    project = Project(name="P", owner_id=1)
    db.add(project)
    db.flush()
    conv = Conversation(project_id=project.id, state=2)  # schema_confirm - waiting
    db.add(conv)
    db.commit()

    from app.services.agent_service import run_and_stream
    events = []
    async for event in run_and_stream(conv.id, lambda: db):
        events.append(event)

    assert any(e["type"] == "waiting" for e in events)
