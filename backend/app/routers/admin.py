from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from app.deps import get_current_user
from app.models.user import User
from app.config import settings

router = APIRouter(prefix="/admin", tags=["admin"])

VALID_PROVIDERS = ("claude", "qwen", "deepseek")


class LLMConfigOut(BaseModel):
    provider: str
    model: str


class LLMConfigIn(BaseModel):
    provider: str
    model: str


def _current_model(provider: str) -> str:
    if provider == "claude":
        return settings.claude_model
    if provider == "qwen":
        return settings.qwen_model
    if provider == "deepseek":
        return settings.deepseek_model
    return ""


@router.get("/config", response_model=LLMConfigOut)
def get_config(current_user: Annotated[User, Depends(get_current_user)]):
    return LLMConfigOut(provider=settings.llm_provider, model=_current_model(settings.llm_provider))


@router.put("/config", response_model=LLMConfigOut)
def update_config(
    body: LLMConfigIn,
    current_user: Annotated[User, Depends(get_current_user)],
):
    if body.provider not in VALID_PROVIDERS:
        raise HTTPException(status_code=400, detail=f"provider must be one of {VALID_PROVIDERS}")
    settings.llm_provider = body.provider
    if body.provider == "claude":
        settings.claude_model = body.model
    elif body.provider == "qwen":
        settings.qwen_model = body.model
    else:
        settings.deepseek_model = body.model
    return LLMConfigOut(provider=settings.llm_provider, model=body.model)
