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
    _get_conversation_or_404(conv_id, db)
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
        yield 'data: {"type": "end"}\n\n'

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
