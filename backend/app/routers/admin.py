from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
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
        raise HTTPException(status_code=400, detail="provider must be 'claude' or 'qwen'")
    settings.llm_provider = body.provider
    if body.provider == "claude":
        settings.claude_model = body.model
    else:
        settings.qwen_model = body.model
    return LLMConfigOut(provider=settings.llm_provider, model=body.model)
