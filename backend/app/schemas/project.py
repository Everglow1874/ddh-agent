from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class ProjectCreate(BaseModel):
    name: str
    description: Optional[str] = None


class ProjectUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    status: Optional[int] = None


class ProjectOut(BaseModel):
    id: int
    name: str
    description: Optional[str]
    owner_id: int
    status: int
    created_at: datetime

    model_config = {"from_attributes": True}


class TableAssociateIn(BaseModel):
    table_ids: list[int]


class AssociateResult(BaseModel):
    associated: int
