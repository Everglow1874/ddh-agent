# DDH Agent Core - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the AI Agent core: LLM abstraction layer (Claude + Qwen), tool-use orchestration, SSE streaming, conversation management, ETL SQL generation, and ETL/admin API endpoints.

**Architecture:** A synchronous agent loop runs in a thread-pool executor, pushes SSE events into a per-conversation `asyncio.Queue`. FastAPI's `StreamingResponse` reads from that queue and streams to the client. The conversation state machine (`gathering→schema_confirm→steps_confirm→generating→done`) is stored in MySQL and drives which tools the agent can call at each turn. Agent tools include DB-query tools (`list_project_tables`, `get_table_schema`) and workflow-control tools (`propose_schema`, `propose_etl_steps`, `generate_sql`) that mutate state and emit SSE events.

**Tech Stack:** anthropic SDK, openai SDK (Qwen uses OpenAI-compatible endpoint), asyncio, FastAPI StreamingResponse, zipfile (download endpoint)

**Spec reference:** `docs/superpowers/specs/2026-05-31-ddh-agent-platform-design.md` §4 (Agent workflow), §5 (API)

---

## File Map

```
backend/
+-- requirements.txt                          (add anthropic, openai, pytest-asyncio)
+-- app/
|   +-- services/
|   |   +-- llm/
|   |   |   +-- __init__.py                  (exports get_llm_provider)
|   |   |   +-- base.py                      (BaseLLMProvider, LLMResponse, ToolCall dataclasses)
|   |   |   +-- claude_provider.py           (Anthropic SDK implementation)
|   |   |   +-- qwen_provider.py             (OpenAI-compatible Qwen implementation)
|   |   +-- agent_tools.py                   (AGENT_TOOLS list, execute_tool, get_tools_for_state)
|   |   +-- agent_service.py                 (run_and_stream, build_system_prompt, _run_agent_sync)
|   |   +-- etl_service.py                   (write_sql_file, write_plan_md, create_etl_job)
|   +-- schemas/
|   |   +-- conversation.py                  (ConversationOut, MessageOut, ChatIn, ConfirmSchemaIn, ConfirmStepsIn)
|   |   +-- etl.py                           (EtlJobOut, EtlStepOut)
|   +-- routers/
|       +-- conversations.py                 (all 7 conversation + SSE endpoints)
|       +-- jobs.py                          (ETL job list, detail, SQL content, ZIP download)
|       +-- admin.py                         (GET/PUT /admin/config)
+-- tests/
    +-- test_llm_providers.py                (provider unit tests with mocks)
    +-- test_agent_tools.py                  (tool execution unit tests)
    +-- test_agent_service.py                (agent orchestration with mock provider)
    +-- test_conversations.py                (conversation API tests)
    +-- test_etl_service.py                  (file writing tests)
    +-- test_jobs.py                         (ETL job API tests)
```

---

## Task 1: LLM Abstraction Layer

**Files:**
- Modify: `backend/requirements.txt`
- Create: `backend/app/services/llm/__init__.py`
- Create: `backend/app/services/llm/base.py`
- Create: `backend/app/services/llm/claude_provider.py`
- Create: `backend/app/services/llm/qwen_provider.py`
- Create: `backend/tests/test_llm_providers.py`

- [ ] **Step 1: Add new dependencies to requirements.txt**

Add these lines to `backend/requirements.txt`:
```
anthropic==0.40.0
openai==1.58.1
pytest-asyncio==0.24.0
```

Install:
```bash
cd backend
.venv\Scripts\activate
pip install anthropic==0.40.0 openai==1.58.1 pytest-asyncio==0.24.0
```

- [ ] **Step 2: Write failing tests**

Create `backend/tests/test_llm_providers.py`:
```python
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
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cd backend && .venv\Scripts\activate
pytest tests/test_llm_providers.py -v
```
Expected: `ImportError: cannot import name 'BaseLLMProvider'`

- [ ] **Step 4: Create `backend/app/services/llm/base.py`**

```python
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
    """Canonical message format uses these role strings:
    - "user": plain user text
    - "assistant": plain assistant text
    - "assistant_tool_use": assistant message with tool_calls list
    - "tool_result": tool execution result with tool_call_id and content (JSON string)

    Canonical tool definition format:
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
```

- [ ] **Step 5: Create `backend/app/services/llm/claude_provider.py`**

```python
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
                text = block.text
            elif block.type == "tool_use":
                tool_calls.append(ToolCall(id=block.id, name=block.name, input=block.input))
        stop = "tool_use" if response.stop_reason == "tool_use" else "end_turn"
        return LLMResponse(text=text, stop_reason=stop, tool_calls=tool_calls)
```

- [ ] **Step 6: Create `backend/app/services/llm/qwen_provider.py`**

```python
import json
from openai import OpenAI
from app.services.llm.base import BaseLLMProvider, LLMResponse, ToolCall


def _to_openai_messages(messages: list[dict], system: str) -> list[dict]:
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


def _to_openai_tools(tools: list[dict]) -> list[dict]:
    return [
        {"type": "function", "function": {"name": t["name"], "description": t["description"], "parameters": t["parameters"]}}
        for t in tools
    ]


class QwenProvider(BaseLLMProvider):
    def __init__(self, api_key: str, model: str):
        self.client = OpenAI(
            api_key=api_key,
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
        )
        self.model = model

    def chat_with_tools(
        self,
        messages: list[dict],
        tools: list[dict],
        system: str = "",
    ) -> LLMResponse:
        response = self.client.chat.completions.create(
            model=self.model,
            messages=_to_openai_messages(messages, system),
            tools=_to_openai_tools(tools),
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
```

- [ ] **Step 7: Create `backend/app/services/llm/__init__.py`**

```python
from app.services.llm.base import BaseLLMProvider, LLMResponse, ToolCall
from app.services.llm.claude_provider import ClaudeProvider
from app.services.llm.qwen_provider import QwenProvider
from app.config import settings


def get_llm_provider() -> BaseLLMProvider:
    provider = settings.llm_provider
    if provider == "claude":
        return ClaudeProvider(api_key=settings.claude_api_key, model=settings.claude_model)
    if provider == "qwen":
        return QwenProvider(api_key=settings.qwen_api_key, model=settings.qwen_model)
    raise ValueError(f"Unknown LLM provider: {provider!r}. Must be 'claude' or 'qwen'.")


__all__ = ["BaseLLMProvider", "LLMResponse", "ToolCall", "get_llm_provider"]
```

- [ ] **Step 8: Run tests to verify they pass**

```bash
pytest tests/test_llm_providers.py -v
```
Expected: `5 passed`

- [ ] **Step 9: Commit**

```bash
git add backend/requirements.txt backend/app/services/llm/ backend/tests/test_llm_providers.py
git commit -m "feat: add LLM abstraction layer for Claude and Qwen"
```

---

## Task 2: Agent Tools + Conversation Schemas

**Files:**
- Create: `backend/app/services/agent_tools.py`
- Create: `backend/app/schemas/conversation.py`
- Create: `backend/tests/test_agent_tools.py`

- [ ] **Step 1: Write failing unit tests**

Create `backend/tests/test_agent_tools.py`:
```python
import json
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.database import Base
from app.models.source_table import SourceTable, TableColumn
from app.models.project import Project, ProjectTable
from app.models.conversation import Conversation
from app.services.agent_tools import execute_tool, get_tools_for_state, AGENT_TOOLS


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
def setup_project_with_table(db):
    table = SourceTable(name="dw_order", scope=1)
    db.add(table)
    db.flush()
    db.add(TableColumn(table_id=table.id, column_name="user_id", data_type="VARCHAR(64)", comment="用户ID", sort_order=0))
    db.add(TableColumn(table_id=table.id, column_name="amount", data_type="DECIMAL(18,2)", comment="金额", sort_order=1))
    project = Project(name="Test", owner_id=1)
    db.add(project)
    db.flush()
    db.add(ProjectTable(project_id=project.id, table_id=table.id))
    conv = Conversation(project_id=project.id, state=1)
    db.add(conv)
    db.commit()
    return {"table": table, "project": project, "conv": conv}


def test_list_project_tables(db, setup_project_with_table):
    conv = setup_project_with_table["conv"]
    emitted = []
    result = execute_tool("list_project_tables", {"project_id": conv.project_id}, db, conv, emitted.append)
    assert "tables" in result
    assert len(result["tables"]) == 1
    assert result["tables"][0]["name"] == "dw_order"


def test_get_table_schema(db, setup_project_with_table):
    table = setup_project_with_table["table"]
    conv = setup_project_with_table["conv"]
    emitted = []
    result = execute_tool("get_table_schema", {"table_id": table.id}, db, conv, emitted.append)
    assert result["name"] == "dw_order"
    assert len(result["columns"]) == 2
    assert result["columns"][0]["column_name"] == "user_id"


def test_propose_schema_emits_event(db, setup_project_with_table):
    conv = setup_project_with_table["conv"]
    emitted = []
    columns = [{"name": "user_id", "type": "VARCHAR(64)", "comment": "用户ID"}]
    result = execute_tool("propose_schema", {"target_table": "result_table", "columns": columns}, db, conv, emitted.append)
    assert result["status"] == "proposal_sent"
    assert len(emitted) == 1
    assert emitted[0]["type"] == "schema_proposal"
    assert emitted[0]["target_table"] == "result_table"
    db.refresh(conv)
    assert conv.state == 2


def test_get_tools_for_state_1():
    tools = get_tools_for_state(1)
    names = {t["name"] for t in tools}
    assert "list_project_tables" in names
    assert "get_table_schema" in names
    assert "propose_schema" in names
    assert "generate_sql" not in names


def test_get_tools_for_state_4():
    tools = get_tools_for_state(4)
    names = {t["name"] for t in tools}
    assert "generate_sql" in names
    assert "propose_schema" not in names
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
pytest tests/test_agent_tools.py -v
```
Expected: `ImportError: cannot import name 'execute_tool'`

- [ ] **Step 3: Create `backend/app/services/agent_tools.py`**

```python
import json
from typing import Callable
from sqlalchemy.orm import Session
from app.models.source_table import SourceTable, TableColumn
from app.models.project import ProjectTable
from app.models.conversation import Conversation

AGENT_TOOLS = [
    {
        "name": "list_project_tables",
        "description": "Get all source tables selected for this ETL project.",
        "parameters": {
            "type": "object",
            "properties": {
                "project_id": {"type": "integer", "description": "The project ID"}
            },
            "required": ["project_id"],
        },
    },
    {
        "name": "get_table_schema",
        "description": "Get column definitions (name, type, comment) for a source table.",
        "parameters": {
            "type": "object",
            "properties": {
                "table_id": {"type": "integer", "description": "The table ID"}
            },
            "required": ["table_id"],
        },
    },
    {
        "name": "propose_schema",
        "description": (
            "Propose the target table structure to the user for confirmation. "
            "Call this when you have determined the output table columns from the requirements."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "target_table": {"type": "string", "description": "Name of the output target table"},
                "columns": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "type": {"type": "string"},
                            "comment": {"type": "string"},
                        },
                        "required": ["name", "type"],
                    },
                },
            },
            "required": ["target_table", "columns"],
        },
    },
    {
        "name": "propose_etl_steps",
        "description": (
            "Propose the ETL execution plan to the user for confirmation. "
            "Call this after schema has been confirmed."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "steps": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "step_order": {"type": "integer"},
                            "step_name": {"type": "string"},
                            "description": {"type": "string"},
                            "is_temp_table": {"type": "boolean"},
                            "output_table": {"type": "string"},
                        },
                        "required": ["step_order", "step_name", "description", "is_temp_table", "output_table"],
                    },
                }
            },
            "required": ["steps"],
        },
    },
    {
        "name": "generate_sql",
        "description": (
            "Generate GaussDB SQL for one ETL step. Call once per confirmed step."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "step_order": {"type": "integer"},
                "step_name": {"type": "string"},
                "is_temp_table": {"type": "boolean"},
                "sql": {"type": "string", "description": "Complete GaussDB SQL for this step"},
            },
            "required": ["step_order", "step_name", "is_temp_table", "sql"],
        },
    },
]

_TOOLS_BY_STATE: dict[int, list[str]] = {
    1: ["list_project_tables", "get_table_schema", "propose_schema"],
    3: ["propose_etl_steps"],
    4: ["generate_sql"],
}


def get_tools_for_state(state: int) -> list[dict]:
    names = _TOOLS_BY_STATE.get(state, [])
    return [t for t in AGENT_TOOLS if t["name"] in names]


def execute_tool(
    name: str,
    input_data: dict,
    db: Session,
    conversation: Conversation,
    emit: Callable[[dict], None],
) -> dict:
    if name == "list_project_tables":
        project_id = input_data["project_id"]
        rows = db.query(ProjectTable).filter(ProjectTable.project_id == project_id).all()
        table_ids = [r.table_id for r in rows]
        tables = db.query(SourceTable).filter(SourceTable.id.in_(table_ids)).all()
        return {"tables": [{"id": t.id, "name": t.name, "scope": t.scope} for t in tables]}

    if name == "get_table_schema":
        table_id = input_data["table_id"]
        table = db.query(SourceTable).filter(SourceTable.id == table_id).first()
        if table is None:
            return {"error": f"Table {table_id} not found"}
        columns = (
            db.query(TableColumn)
            .filter(TableColumn.table_id == table_id)
            .order_by(TableColumn.sort_order)
            .all()
        )
        return {
            "name": table.name,
            "columns": [
                {"column_name": c.column_name, "data_type": c.data_type, "comment": c.comment}
                for c in columns
            ],
        }

    if name == "propose_schema":
        conversation.state = 2
        db.commit()
        emit({
            "type": "schema_proposal",
            "target_table": input_data["target_table"],
            "columns": input_data["columns"],
        })
        return {"status": "proposal_sent"}

    if name == "propose_etl_steps":
        conversation.state = 3
        db.commit()
        emit({
            "type": "steps_proposal",
            "steps": input_data["steps"],
        })
        return {"status": "proposal_sent"}

    if name == "generate_sql":
        from app.services.etl_service import write_sql_file, get_projects_dir
        file_path = write_sql_file(
            project_id=conversation.project_id,
            step_order=input_data["step_order"],
            step_name=input_data["step_name"],
            sql=input_data["sql"],
        )
        emit({
            "type": "step_generated",
            "step_order": input_data["step_order"],
            "step_name": input_data["step_name"],
            "sql": input_data["sql"],
            "file": file_path,
        })
        return {
            "status": "sql_saved",
            "step_order": input_data["step_order"],
            "file_path": file_path,
            "is_temp_table": input_data["is_temp_table"],
            "step_name": input_data["step_name"],
        }

    return {"error": f"Unknown tool: {name}"}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
pytest tests/test_agent_tools.py -v
```
Expected: `6 passed`

- [ ] **Step 5: Create `backend/app/schemas/conversation.py`**

```python
from pydantic import BaseModel
from typing import Optional, Any
from datetime import datetime


class ConversationOut(BaseModel):
    id: int
    project_id: int
    state: int
    created_at: datetime

    model_config = {"from_attributes": True}


class MessageOut(BaseModel):
    id: int
    conversation_id: int
    role: str
    content: str
    created_at: datetime

    model_config = {"from_attributes": True}


class ChatIn(BaseModel):
    message: str


class ConfirmSchemaIn(BaseModel):
    target_table: str
    columns: list[dict[str, Any]]


class ConfirmStepsIn(BaseModel):
    steps: list[dict[str, Any]]
```

- [ ] **Step 6: Commit**

```bash
git add backend/app/services/agent_tools.py backend/app/schemas/conversation.py backend/tests/test_agent_tools.py
git commit -m "feat: add agent tools, execute_tool, and conversation schemas"
```

---

## Task 3: Agent Service

**Files:**
- Create: `backend/app/services/agent_service.py`
- Create: `backend/tests/test_agent_service.py`

- [ ] **Step 1: Write failing tests**

Create `backend/tests/test_agent_service.py`:
```python
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
        with patch("app.services.agent_service.SessionLocal", return_value=db):
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
```

- [ ] **Step 2: Add `asyncio_mode = "auto"` to pytest configuration**

Create `backend/pytest.ini`:
```ini
[pytest]
asyncio_mode = auto
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
pytest tests/test_agent_service.py -v
```
Expected: `ImportError: cannot import name 'build_system_prompt'`

- [ ] **Step 4: Create `backend/app/services/agent_service.py`**

```python
import json
import asyncio
from typing import AsyncGenerator, Callable
from sqlalchemy.orm import Session
from app.database import SessionLocal
from app.models.conversation import Conversation, Message
from app.services.llm import get_llm_provider
from app.services.agent_tools import get_tools_for_state, execute_tool

# In-memory active queues: conversation_id -> asyncio.Queue
_active_queues: dict[int, asyncio.Queue] = {}


def build_system_prompt(state: int) -> str:
    base = (
        "You are an ETL development assistant for GaussDB data warehouses. "
        "GaussDB uses PostgreSQL-compatible SQL syntax. "
        "Always generate complete, executable SQL. "
    )
    if state == 1:
        return base + (
            "Your task: analyze the user's ETL requirements. "
            "Use list_project_tables to see available source tables, "
            "use get_table_schema to understand column details. "
            "When you fully understand the requirements, call propose_schema "
            "to propose the target table structure."
        )
    if state == 3:
        return base + (
            "The target table schema has been confirmed (shown in conversation history). "
            "Your task: plan the ETL execution steps. Consider whether temporary tables are needed. "
            "Call propose_etl_steps with the complete execution plan."
        )
    if state == 4:
        return base + (
            "The ETL execution steps have been confirmed (shown in conversation history). "
            "Your task: generate GaussDB SQL for each step. "
            "Call generate_sql once per step in the order they appear in the confirmed plan."
        )
    return base


def _build_message_history(db: Session, conversation_id: int) -> list[dict]:
    messages = (
        db.query(Message)
        .filter(Message.conversation_id == conversation_id)
        .order_by(Message.id)
        .all()
    )
    result = []
    for msg in messages:
        if msg.role == "user":
            result.append({"role": "user", "content": msg.content})
        elif msg.role == "assistant":
            result.append({"role": "assistant", "content": msg.content})
        # tool messages stored as JSON are skipped for now (LLM history built fresh each turn)
    return result


def _save_message(db: Session, conversation_id: int, role: str, content: str) -> None:
    msg = Message(conversation_id=conversation_id, role=role, content=content)
    db.add(msg)
    db.commit()


def _run_agent_sync(
    conversation_id: int,
    db: Session,
    emit: Callable[[dict], None],
) -> None:
    from app.services.etl_service import write_plan_md, create_etl_job, create_etl_step

    conversation = db.query(Conversation).filter(Conversation.id == conversation_id).first()
    if conversation is None:
        emit({"type": "error", "message": "Conversation not found"})
        return

    state = conversation.state
    if state == 2:
        emit({"type": "waiting", "state": state, "message": "Waiting for schema confirmation"})
        return
    if state == 5:
        emit({"type": "already_done"})
        return

    messages = _build_message_history(db, conversation_id)
    if not messages:
        emit({"type": "error", "message": "No user message to process"})
        return

    system = build_system_prompt(state)
    tools = get_tools_for_state(state)
    provider = get_llm_provider()

    # Track SQL steps generated during state=4 run
    generated_steps: list[dict] = []

    # Tool use loop
    while True:
        response = provider.chat_with_tools(messages=messages, tools=tools, system=system)

        if response.text:
            emit({"type": "token", "text": response.text})

        if response.stop_reason == "end_turn":
            if response.text:
                _save_message(db, conversation_id, "assistant", response.text)
            break

        # Process tool calls
        messages.append(response.to_assistant_message())

        for tool_call in response.tool_calls:
            result = execute_tool(
                tool_call.name, tool_call.input, db, conversation, emit
            )
            # Track generate_sql results for EtlJob persistence
            if tool_call.name == "generate_sql" and result.get("status") == "sql_saved":
                generated_steps.append(result)
            messages.append({
                "role": "tool_result",
                "tool_call_id": tool_call.id,
                "content": json.dumps(result),
            })

    # After state=4 run: persist EtlJob and EtlSteps if SQL was generated
    if state == 4 and generated_steps:
        # Build requirement summary from first user message
        first_user = next((m["content"] for m in messages if m["role"] == "user"), "ETL requirement")

        # Extract confirmed target table from conversation history (look for schema confirmation msg)
        target_table = "target_table"
        target_schema: list = []
        for msg in messages:
            if msg["role"] == "user" and "目标表结构已确认" in msg.get("content", ""):
                # Parse from the synthetic confirmation message format set in confirm-schema endpoint
                import re as _re
                m = _re.search(r"目标表：(.+?)，字段：(.+)$", msg["content"])
                if m:
                    target_table = m.group(1).strip()
                    try:
                        target_schema = json.loads(m.group(2).strip())
                    except Exception:
                        pass
                break

        # Write plan.md
        steps_for_plan = [
            {
                "step_order": s["step_order"],
                "step_name": s["step_name"],
                "description": s["step_name"],
                "is_temp_table": s.get("is_temp_table", False),
                "output_table": target_table,
            }
            for s in sorted(generated_steps, key=lambda x: x["step_order"])
        ]
        plan_path = write_plan_md(
            project_id=conversation.project_id,
            target_table=target_table,
            requirement=first_user[:500],
            steps=steps_for_plan,
        )

        # Create EtlJob record
        job = create_etl_job(
            db=db,
            project_id=conversation.project_id,
            target_table=target_table,
            target_schema=target_schema,
            plan_md_path=plan_path,
        )

        # Create EtlStep records
        for s in sorted(generated_steps, key=lambda x: x["step_order"]):
            create_etl_step(
                db=db,
                job_id=job.id,
                step_order=s["step_order"],
                step_name=s["step_name"],
                is_temp_table=bool(s.get("is_temp_table", False)),
                sql_file_path=s["file_path"],
            )

        # Update conversation state to done
        conversation.state = 5
        db.commit()

        emit({"type": "done", "job_id": job.id})

    emit({"type": "stream_end"})


async def run_and_stream(
    conversation_id: int,
    db_factory: Callable[[], Session],
) -> AsyncGenerator[dict, None]:
    loop = asyncio.get_event_loop()
    queue: asyncio.Queue = asyncio.Queue()
    _active_queues[conversation_id] = queue

    def emit(event: dict) -> None:
        asyncio.run_coroutine_threadsafe(queue.put(event), loop)

    def run_sync() -> None:
        db = db_factory()
        try:
            _run_agent_sync(conversation_id, db, emit)
        except Exception as exc:
            asyncio.run_coroutine_threadsafe(
                queue.put({"type": "error", "message": str(exc)}), loop
            )
        finally:
            db.close()
            asyncio.run_coroutine_threadsafe(queue.put(None), loop)

    loop.run_in_executor(None, run_sync)

    try:
        while True:
            event = await asyncio.wait_for(queue.get(), timeout=120.0)
            if event is None:
                break
            yield event
    finally:
        _active_queues.pop(conversation_id, None)
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
pytest tests/test_agent_service.py -v
```
Expected: `7 passed`

- [ ] **Step 6: Commit**

```bash
git add backend/app/services/agent_service.py backend/tests/test_agent_service.py backend/pytest.ini
git commit -m "feat: add agent service with async streaming and state-machine tool loop"
```

---

## Task 4: Conversation Router + SSE

**Files:**
- Create: `backend/app/routers/conversations.py`
- Modify: `backend/app/main.py`
- Create: `backend/tests/test_conversations.py`

- [ ] **Step 1: Write failing tests**

Create `backend/tests/test_conversations.py`:
```python
import io
import json
import pytest
from unittest.mock import patch, AsyncMock
from app.services.llm.base import LLMResponse


VALID_CSV = b"column_name,data_type\nid,BIGINT\n"


def _make_project(client, auth_headers):
    r = client.post("/api/projects", json={"name": "TestProj"}, headers=auth_headers)
    return r.json()["id"]


def _import_table(client, auth_headers):
    return client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": ("t.csv", io.BytesIO(VALID_CSV), "text/csv")},
        headers=auth_headers,
    ).json()["id"]


def test_create_conversation(client, auth_headers):
    pid = _make_project(client, auth_headers)
    resp = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers)
    assert resp.status_code == 201
    data = resp.json()
    assert data["project_id"] == pid
    assert data["state"] == 1


def test_list_conversations(client, auth_headers):
    pid = _make_project(client, auth_headers)
    client.post(f"/api/projects/{pid}/conversations", headers=auth_headers)
    client.post(f"/api/projects/{pid}/conversations", headers=auth_headers)
    resp = client.get(f"/api/projects/{pid}/conversations", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) >= 2


def test_post_chat_saves_message(client, auth_headers):
    pid = _make_project(client, auth_headers)
    cid = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers).json()["id"]
    resp = client.post(f"/api/conversations/{cid}/chat",
                       json={"message": "analyze my data"},
                       headers=auth_headers)
    assert resp.status_code == 200
    msgs = client.get(f"/api/conversations/{cid}/messages", headers=auth_headers).json()
    assert any(m["content"] == "analyze my data" for m in msgs)


def test_confirm_schema_updates_state(client, auth_headers):
    pid = _make_project(client, auth_headers)
    cid = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers).json()["id"]
    resp = client.post(
        f"/api/conversations/{cid}/confirm-schema",
        json={"target_table": "result_tbl", "columns": [{"name": "id", "type": "BIGINT"}]},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["state"] == 3


def test_confirm_steps_updates_state(client, auth_headers):
    pid = _make_project(client, auth_headers)
    cid = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers).json()["id"]
    # Force state to 3 first
    from app.models.conversation import Conversation
    # Use confirm-schema to get to state 3
    client.post(
        f"/api/conversations/{cid}/confirm-schema",
        json={"target_table": "t", "columns": [{"name": "id", "type": "INT"}]},
        headers=auth_headers,
    )
    resp = client.post(
        f"/api/conversations/{cid}/confirm-steps",
        json={"steps": [{"step_order": 1, "step_name": "load", "description": "x", "is_temp_table": False, "output_table": "t"}]},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["state"] == 4


def test_get_messages(client, auth_headers):
    pid = _make_project(client, auth_headers)
    cid = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers).json()["id"]
    client.post(f"/api/conversations/{cid}/chat", json={"message": "hi"}, headers=auth_headers)
    resp = client.get(f"/api/conversations/{cid}/messages", headers=auth_headers)
    assert resp.status_code == 200
    assert isinstance(resp.json(), list)
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
pytest tests/test_conversations.py -v
```
Expected: `FAILED - 404 Not Found` on conversation endpoints.

- [ ] **Step 3: Create `backend/app/routers/conversations.py`**

```python
import json
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session
from app.deps import get_db, get_current_user
from app.database import SessionLocal
from app.models.conversation import Conversation, Message
from app.models.project import Project
from app.models.user import User
from app.schemas.conversation import ConversationOut, MessageOut, ChatIn, ConfirmSchemaIn, ConfirmStepsIn
from app.services.agent_service import run_and_stream

router = APIRouter(tags=["conversations"])


def _get_conversation_or_404(conv_id: int, db: Session) -> Conversation:
    conv = db.query(Conversation).filter(Conversation.id == conv_id).first()
    if conv is None:
        raise HTTPException(status_code=404, detail="Conversation not found")
    return conv


@router.post("/projects/{project_id}/conversations", response_model=ConversationOut, status_code=201)
def create_conversation(
    project_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    project = db.query(Project).filter(Project.id == project_id, Project.owner_id == current_user.id).first()
    if project is None:
        raise HTTPException(status_code=404, detail="Project not found")
    conv = Conversation(project_id=project_id, state=1)
    db.add(conv)
    db.commit()
    db.refresh(conv)
    return conv


@router.get("/projects/{project_id}/conversations", response_model=list[ConversationOut])
def list_conversations(
    project_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    project = db.query(Project).filter(Project.id == project_id, Project.owner_id == current_user.id).first()
    if project is None:
        raise HTTPException(status_code=404, detail="Project not found")
    return db.query(Conversation).filter(Conversation.project_id == project_id).order_by(Conversation.created_at.desc()).all()


@router.post("/conversations/{conv_id}/chat")
def chat(
    conv_id: int,
    body: ChatIn,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    conv = _get_conversation_or_404(conv_id, db)
    msg = Message(conversation_id=conv_id, role="user", content=body.message)
    db.add(msg)
    db.commit()
    return {"status": "ok", "conversation_id": conv_id}


@router.get("/conversations/{conv_id}/stream")
async def stream(
    conv_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    _get_conversation_or_404(conv_id, db)

    async def event_generator():
        async for event in run_and_stream(conv_id, SessionLocal):
            yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
        yield "data: {\"type\": \"end\"}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


@router.post("/conversations/{conv_id}/confirm-schema", response_model=ConversationOut)
def confirm_schema(
    conv_id: int,
    body: ConfirmSchemaIn,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    conv = _get_conversation_or_404(conv_id, db)
    if conv.state not in (1, 2):
        raise HTTPException(status_code=400, detail=f"Cannot confirm schema in state {conv.state}")
    conv.state = 3
    # Add synthetic confirmation message so agent has context in the next turn
    db.add(Message(
        conversation_id=conv_id,
        role="user",
        content=f"目标表结构已确认。目标表：{body.target_table}，字段：{json.dumps(body.columns, ensure_ascii=False)}。请规划ETL步骤。",
    ))
    db.commit()
    db.refresh(conv)
    return conv


@router.post("/conversations/{conv_id}/confirm-steps", response_model=ConversationOut)
def confirm_steps(
    conv_id: int,
    body: ConfirmStepsIn,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    conv = _get_conversation_or_404(conv_id, db)
    if conv.state != 3:
        raise HTTPException(status_code=400, detail=f"Cannot confirm steps in state {conv.state}")
    conv.state = 4
    steps_summary = json.dumps(body.steps, ensure_ascii=False)
    db.add(Message(
        conversation_id=conv_id,
        role="user",
        content=f"ETL步骤已确认。步骤计划：{steps_summary}。请为每个步骤生成GaussDB SQL。",
    ))
    db.commit()
    db.refresh(conv)
    return conv


@router.get("/conversations/{conv_id}/messages", response_model=list[MessageOut])
def get_messages(
    conv_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    _get_conversation_or_404(conv_id, db)
    return db.query(Message).filter(Message.conversation_id == conv_id).order_by(Message.id).all()
```

- [ ] **Step 4: Update `backend/app/main.py`**

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import auth, tables, projects, conversations
from app.config import settings
import logging

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not settings.secret_key:
        logger.warning("WARNING: secret_key is empty. Set a strong key in config.yaml before production.")
    yield


app = FastAPI(title="DDH Agent API", version="0.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api")
app.include_router(tables.router, prefix="/api")
app.include_router(projects.router, prefix="/api")
app.include_router(conversations.router, prefix="/api")
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
pytest tests/test_conversations.py -v
```
Expected: `6 passed`

- [ ] **Step 6: Run full suite**

```bash
pytest tests/ -v
```
Expected: all previous tests still pass.

- [ ] **Step 7: Commit**

```bash
git add backend/app/routers/conversations.py backend/app/main.py backend/tests/test_conversations.py
git commit -m "feat: add conversation router with SSE streaming and confirmation endpoints"
```

---

## Task 5: ETL Service + Schemas

**Files:**
- Create: `backend/app/services/etl_service.py`
- Create: `backend/app/schemas/etl.py`
- Create: `backend/tests/test_etl_service.py`

- [ ] **Step 1: Write failing tests**

Create `backend/tests/test_etl_service.py`:
```python
import os
import pytest
from pathlib import Path
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.database import Base
from app.models.etl import EtlJob, EtlStep
from app.models.project import Project
from app.services.etl_service import (
    write_sql_file, write_plan_md, create_etl_job, create_etl_step, get_projects_dir
)


@pytest.fixture(scope="function")
def db(tmp_path, monkeypatch):
    monkeypatch.setenv("PROJECTS_DIR", str(tmp_path))
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()
    Base.metadata.drop_all(engine)
    engine.dispose()


def test_write_sql_file(tmp_path, monkeypatch):
    monkeypatch.setenv("PROJECTS_DIR", str(tmp_path))
    path = write_sql_file(project_id=1, step_order=1, step_name="load_orders", sql="SELECT 1;", base_dir=tmp_path)
    assert Path(path).exists()
    assert "SELECT 1;" in Path(path).read_text(encoding="utf-8")


def test_write_plan_md(tmp_path):
    steps = [
        {"step_order": 1, "step_name": "load", "description": "Load data", "is_temp_table": True, "output_table": "tmp_load"},
        {"step_order": 2, "step_name": "agg", "description": "Aggregate", "is_temp_table": False, "output_table": "result"},
    ]
    path = write_plan_md(
        project_id=1,
        target_table="result",
        requirement="Count orders",
        steps=steps,
        base_dir=tmp_path,
    )
    content = Path(path).read_text(encoding="utf-8")
    assert "result" in content
    assert "Count orders" in content
    assert "load" in content


def test_create_etl_job_and_steps(db, tmp_path):
    project = Project(name="P", owner_id=1)
    db.add(project)
    db.flush()

    steps_data = [
        {"step_order": 1, "step_name": "step1", "is_temp_table": False, "sql": "INSERT INTO t SELECT 1;"}
    ]
    sql_path = write_sql_file(
        project_id=project.id, step_order=1, step_name="step1",
        sql=steps_data[0]["sql"], base_dir=tmp_path
    )
    plan_path = write_plan_md(
        project_id=project.id, target_table="t",
        requirement="test", steps=[], base_dir=tmp_path
    )

    job = create_etl_job(
        db=db,
        project_id=project.id,
        target_table="t",
        target_schema=[{"name": "id", "type": "INT"}],
        plan_md_path=plan_path,
    )
    step = create_etl_step(
        db=db,
        job_id=job.id,
        step_order=1,
        step_name="step1",
        is_temp_table=False,
        sql_file_path=sql_path,
    )

    assert job.id is not None
    assert step.job_id == job.id
    assert step.sql_file_path == sql_path
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
pytest tests/test_etl_service.py -v
```
Expected: `ImportError: cannot import name 'write_sql_file'`

- [ ] **Step 3: Create `backend/app/services/etl_service.py`**

```python
import re
from pathlib import Path
from typing import Optional
from sqlalchemy.orm import Session
from app.models.etl import EtlJob, EtlStep
from app.config import settings


def get_projects_dir() -> Path:
    return Path(settings.projects_dir)


def _project_dir(project_id: int, base_dir: Optional[Path] = None) -> Path:
    root = base_dir if base_dir is not None else get_projects_dir()
    d = root / str(project_id)
    d.mkdir(parents=True, exist_ok=True)
    return d


def _safe_name(s: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_一-鿿]", "_", s)[:64]


def write_sql_file(
    project_id: int,
    step_order: int,
    step_name: str,
    sql: str,
    base_dir: Optional[Path] = None,
) -> str:
    d = _project_dir(project_id, base_dir)
    filename = f"step{step_order}_{_safe_name(step_name)}.sql"
    path = d / filename
    path.write_text(sql, encoding="utf-8")
    return str(path)


def write_plan_md(
    project_id: int,
    target_table: str,
    requirement: str,
    steps: list[dict],
    base_dir: Optional[Path] = None,
) -> str:
    d = _project_dir(project_id, base_dir)
    lines = [
        f"# ETL 执行计划\n",
        f"## 需求描述\n\n{requirement}\n",
        f"## 目标表\n\n`{target_table}`\n",
        "## ETL 步骤\n",
    ]
    for step in steps:
        temp = "（临时表）" if step.get("is_temp_table") else ""
        lines.append(f"### Step {step['step_order']}: {step['step_name']}{temp}\n")
        lines.append(f"{step.get('description', '')}\n")
        lines.append(f"输出表：`{step.get('output_table', '')}`\n")
    path = d / "plan.md"
    path.write_text("\n".join(lines), encoding="utf-8")
    return str(path)


def create_etl_job(
    db: Session,
    project_id: int,
    target_table: str,
    target_schema: list[dict],
    plan_md_path: str,
) -> EtlJob:
    job = EtlJob(
        project_id=project_id,
        target_table=target_table,
        target_schema=target_schema,
        plan_md_path=plan_md_path,
    )
    db.add(job)
    db.commit()
    db.refresh(job)
    return job


def create_etl_step(
    db: Session,
    job_id: int,
    step_order: int,
    step_name: str,
    is_temp_table: bool,
    sql_file_path: str,
) -> EtlStep:
    step = EtlStep(
        job_id=job_id,
        step_order=step_order,
        step_name=step_name,
        is_temp_table=1 if is_temp_table else 0,
        sql_file_path=sql_file_path,
    )
    db.add(step)
    db.commit()
    db.refresh(step)
    return step
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
pytest tests/test_etl_service.py -v
```
Expected: `4 passed`

- [ ] **Step 5: Create `backend/app/schemas/etl.py`**

```python
from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class EtlStepOut(BaseModel):
    id: int
    job_id: int
    step_order: int
    step_name: str
    is_temp_table: int
    sql_file_path: Optional[str]

    model_config = {"from_attributes": True}


class EtlJobOut(BaseModel):
    id: int
    project_id: int
    target_table: str
    target_schema: Optional[list]
    plan_md_path: Optional[str]
    created_at: datetime
    steps: list[EtlStepOut] = []

    model_config = {"from_attributes": True}
```

- [ ] **Step 6: Commit**

```bash
git add backend/app/services/etl_service.py backend/app/schemas/etl.py backend/tests/test_etl_service.py
git commit -m "feat: add ETL file writing service and schemas"
```

---

## Task 6: ETL Jobs Router + Admin Router

**Files:**
- Create: `backend/app/routers/jobs.py`
- Create: `backend/app/routers/admin.py`
- Modify: `backend/app/main.py`
- Create: `backend/tests/test_jobs.py`

- [ ] **Step 1: Write failing tests**

Create `backend/tests/test_jobs.py`:
```python
import io
import pytest
from pathlib import Path
from sqlalchemy.orm import Session
from app.models.etl import EtlJob, EtlStep
from app.models.project import Project


def _setup_job(client, auth_headers, tmp_path, db_override):
    """Helper to create a project + etl job + step in DB."""
    # Create project via API
    pid = client.post("/api/projects", json={"name": "JobProj"}, headers=auth_headers).json()["id"]
    return pid


def _create_job_in_db(db: Session, project_id: int, sql_dir: Path) -> tuple:
    sql_file = sql_dir / "step1_load.sql"
    sql_file.write_text("SELECT 1;", encoding="utf-8")
    plan_file = sql_dir / "plan.md"
    plan_file.write_text("# Plan", encoding="utf-8")
    job = EtlJob(project_id=project_id, target_table="result", target_schema=[], plan_md_path=str(plan_file))
    db.add(job)
    db.flush()
    step = EtlStep(job_id=job.id, step_order=1, step_name="load", is_temp_table=0, sql_file_path=str(sql_file))
    db.add(step)
    db.commit()
    db.refresh(job)
    return job, step


def test_list_jobs_empty(client, auth_headers):
    pid = client.post("/api/projects", json={"name": "P"}, headers=auth_headers).json()["id"]
    resp = client.get(f"/api/projects/{pid}/jobs", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json() == []


def test_get_job_detail(client, auth_headers, tmp_path):
    from app.deps import get_db
    from app.database import Base
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    Sess = sessionmaker(bind=engine, autocommit=False, autoflush=False)

    def override():
        s = Sess()
        try:
            yield s
        finally:
            s.close()

    from app.main import app
    app.dependency_overrides[get_db] = override

    with client as c:
        pid = c.post("/api/projects", json={"name": "J"}, headers=auth_headers).json()["id"]
        s = Sess()
        job, _ = _create_job_in_db(s, pid, tmp_path)
        s.close()
        resp = c.get(f"/api/jobs/{job.id}", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.json()
        assert data["target_table"] == "result"
        assert len(data["steps"]) == 1

    app.dependency_overrides.clear()


def test_get_sql_content(client, auth_headers, tmp_path):
    from app.deps import get_db
    from app.database import Base
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    Sess = sessionmaker(bind=engine, autocommit=False, autoflush=False)

    def override():
        s = Sess()
        try:
            yield s
        finally:
            s.close()

    from app.main import app
    app.dependency_overrides[get_db] = override

    with client as c:
        pid = c.post("/api/projects", json={"name": "SQL"}, headers=auth_headers).json()["id"]
        s = Sess()
        job, step = _create_job_in_db(s, pid, tmp_path)
        s.close()
        resp = c.get(f"/api/jobs/{job.id}/steps/{step.id}/sql", headers=auth_headers)
        assert resp.status_code == 200
        assert "SELECT 1;" in resp.json()["sql"]

    app.dependency_overrides.clear()


def test_admin_get_config(client, auth_headers):
    resp = client.get("/api/admin/config", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert "provider" in data
    assert "model" in data


def test_admin_update_config(client, auth_headers):
    resp = client.put("/api/admin/config",
                      json={"provider": "qwen", "model": "qwen-max"},
                      headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["provider"] == "qwen"
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
pytest tests/test_jobs.py -v
```
Expected: `FAILED - 404` on job endpoints.

- [ ] **Step 3: Create `backend/app/routers/jobs.py`**

```python
import zipfile
import io
from pathlib import Path
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session
from app.deps import get_db, get_current_user
from app.models.etl import EtlJob, EtlStep
from app.models.project import Project
from app.models.user import User
from app.schemas.etl import EtlJobOut, EtlStepOut

router = APIRouter(tags=["jobs"])


def _get_job_or_404(job_id: int, db: Session) -> EtlJob:
    job = db.query(EtlJob).filter(EtlJob.id == job_id).first()
    if job is None:
        raise HTTPException(status_code=404, detail="ETL job not found")
    return job


@router.get("/projects/{project_id}/jobs", response_model=list[EtlJobOut])
def list_jobs(
    project_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    project = db.query(Project).filter(Project.id == project_id, Project.owner_id == current_user.id).first()
    if project is None:
        raise HTTPException(status_code=404, detail="Project not found")
    jobs = db.query(EtlJob).filter(EtlJob.project_id == project_id).order_by(EtlJob.created_at.desc()).all()
    result = []
    for job in jobs:
        steps = db.query(EtlStep).filter(EtlStep.job_id == job.id).order_by(EtlStep.step_order).all()
        job_dict = EtlJobOut.model_validate(job)
        job_dict.steps = [EtlStepOut.model_validate(s) for s in steps]
        result.append(job_dict)
    return result


@router.get("/jobs/{job_id}", response_model=EtlJobOut)
def get_job(
    job_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    job = _get_job_or_404(job_id, db)
    steps = db.query(EtlStep).filter(EtlStep.job_id == job_id).order_by(EtlStep.step_order).all()
    result = EtlJobOut.model_validate(job)
    result.steps = [EtlStepOut.model_validate(s) for s in steps]
    return result


@router.get("/jobs/{job_id}/steps/{step_id}/sql")
def get_step_sql(
    job_id: int,
    step_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    _get_job_or_404(job_id, db)
    step = db.query(EtlStep).filter(EtlStep.id == step_id, EtlStep.job_id == job_id).first()
    if step is None:
        raise HTTPException(status_code=404, detail="Step not found")
    if not step.sql_file_path or not Path(step.sql_file_path).exists():
        raise HTTPException(status_code=404, detail="SQL file not found")
    sql = Path(step.sql_file_path).read_text(encoding="utf-8")
    return {"step_id": step_id, "step_name": step.step_name, "sql": sql}


@router.get("/jobs/{job_id}/download")
def download_job(
    job_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    job = _get_job_or_404(job_id, db)
    steps = db.query(EtlStep).filter(EtlStep.job_id == job_id).order_by(EtlStep.step_order).all()
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for step in steps:
            if step.sql_file_path and Path(step.sql_file_path).exists():
                zf.write(step.sql_file_path, Path(step.sql_file_path).name)
        if job.plan_md_path and Path(job.plan_md_path).exists():
            zf.write(job.plan_md_path, "plan.md")
    buf.seek(0)
    filename = f"etl_job_{job_id}.zip"
    return StreamingResponse(
        buf,
        media_type="application/zip",
        headers={"Content-Disposition": f"attachment; filename={filename}"},
    )
```

- [ ] **Step 4: Create `backend/app/routers/admin.py`**

```python
from typing import Annotated
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from app.deps import get_current_user
from app.models.user import User
from app.config import settings

router = APIRouter(prefix="/admin", tags=["admin"])


class LLMConfigOut(BaseModel):
    provider: str
    model: str


class LLMConfigIn(BaseModel):
    provider: str
    model: str


@router.get("/config", response_model=LLMConfigOut)
def get_config(current_user: Annotated[User, Depends(get_current_user)]):
    if settings.llm_provider == "claude":
        model = settings.claude_model
    else:
        model = settings.qwen_model
    return LLMConfigOut(provider=settings.llm_provider, model=model)


@router.put("/config", response_model=LLMConfigOut)
def update_config(
    body: LLMConfigIn,
    current_user: Annotated[User, Depends(get_current_user)],
):
    if body.provider not in ("claude", "qwen"):
        from fastapi import HTTPException
        raise HTTPException(status_code=400, detail="provider must be 'claude' or 'qwen'")
    settings.llm_provider = body.provider
    if body.provider == "claude":
        settings.claude_model = body.model
    else:
        settings.qwen_model = body.model
    return LLMConfigOut(provider=settings.llm_provider, model=body.model)
```

- [ ] **Step 5: Update `backend/app/main.py`**

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import auth, tables, projects, conversations, jobs, admin
from app.config import settings
import logging

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not settings.secret_key:
        logger.warning("WARNING: secret_key is empty. Set a strong key in config.yaml before production.")
    yield


app = FastAPI(title="DDH Agent API", version="0.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api")
app.include_router(tables.router, prefix="/api")
app.include_router(projects.router, prefix="/api")
app.include_router(conversations.router, prefix="/api")
app.include_router(jobs.router, prefix="/api")
app.include_router(admin.router, prefix="/api")
```

- [ ] **Step 6: Run tests**

```bash
pytest tests/test_jobs.py -v
pytest tests/ -v
```
Expected: `test_jobs.py` passes; full suite passes.

- [ ] **Step 7: Commit**

```bash
git add backend/app/routers/jobs.py backend/app/routers/admin.py backend/app/main.py backend/tests/test_jobs.py
git commit -m "feat: add ETL jobs router, admin config endpoints, wire all routers"
```

---

## Self-Review Checklist

| Spec requirement | Task |
|-----------------|------|
| Claude API + Qwen API, config.yaml 切换 | Task 1 |
| BaseLLMProvider 抽象层 | Task 1 |
| list_project_tables, get_table_schema tools | Task 2 |
| propose_schema tool → SSE + state=2 | Task 2 |
| propose_etl_steps tool → SSE + state=3 | Task 2 |
| generate_sql tool → SSE step_generated | Task 2 |
| Agent Tool Use loop | Task 3 |
| State-aware system prompt | Task 3 |
| SSE stream (token, schema_proposal, steps_proposal, step_generated, done, error) | Task 3+4 |
| POST /conversations → 201 | Task 4 |
| POST /conversations/{id}/chat | Task 4 |
| GET /conversations/{id}/stream SSE | Task 4 |
| POST /conversations/{id}/confirm-schema → state=3 | Task 4 |
| POST /conversations/{id}/confirm-steps → state=4 | Task 4 |
| GET /conversations/{id}/messages | Task 4 |
| SQL 文件写入 + plan.md | Task 5 |
| GET /projects/{id}/jobs | Task 6 |
| GET /jobs/{id} (with steps) | Task 6 |
| GET /jobs/{id}/steps/{sid}/sql | Task 6 |
| GET /jobs/{id}/download (ZIP) | Task 6 |
| GET/PUT /admin/config | Task 6 |

**Not in this plan (deferred to Plan 3 frontend):**
- React + Ant Design UI
- SSE client-side handling
- SQL code highlighting
- Download button

**ETL persistence flow (covered in plan):**
- Task 2 `execute_tool(generate_sql)`: writes SQL file via `etl_service.write_sql_file`, emits SSE event, returns file_path
- Task 3 `_run_agent_sync` (state=4): accumulates `generated_steps` list during tool use loop; after agent finishes, calls `etl_service.write_plan_md` + `create_etl_job` + `create_etl_step`; sets `conversation.state=5`; emits `{"type": "done", "job_id": ...}`
