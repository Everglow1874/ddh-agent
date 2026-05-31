import json
import asyncio
import re as _re
from typing import AsyncGenerator, Callable
from sqlalchemy.orm import Session
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
            if tool_call.name == "generate_sql" and result.get("status") == "sql_saved":
                generated_steps.append(result)
            messages.append({
                "role": "tool_result",
                "tool_call_id": tool_call.id,
                "content": json.dumps(result),
            })

    # After state=4 run: persist EtlJob and EtlSteps if SQL was generated
    if state == 4 and generated_steps:
        from app.services.etl_service import write_plan_md, create_etl_job, create_etl_step
        first_user = next(
            (m["content"] for m in messages if m["role"] == "user"), "ETL requirement"
        )

        # Extract confirmed target table from conversation history
        target_table = "target_table"
        target_schema: list = []
        for msg in messages:
            if msg["role"] == "user" and "目标表结构已确认" in msg.get("content", ""):
                m = _re.search(r"目标表：(.+?)，字段：(.+)$", msg["content"])
                if m:
                    target_table = m.group(1).strip()
                    try:
                        target_schema = json.loads(m.group(2).strip())
                    except Exception:
                        pass
                break

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
        job = create_etl_job(
            db=db,
            project_id=conversation.project_id,
            target_table=target_table,
            target_schema=target_schema,
            plan_md_path=plan_path,
        )
        for s in sorted(generated_steps, key=lambda x: x["step_order"]):
            create_etl_step(
                db=db,
                job_id=job.id,
                step_order=s["step_order"],
                step_name=s["step_name"],
                is_temp_table=bool(s.get("is_temp_table", False)),
                sql_file_path=s["file_path"],
            )
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
