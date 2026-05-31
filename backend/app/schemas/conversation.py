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
