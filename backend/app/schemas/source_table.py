from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class ColumnOut(BaseModel):
    id: int
    column_name: str
    data_type: str
    comment: Optional[str]
    sort_order: int

    model_config = {"from_attributes": True}


class TableOut(BaseModel):
    id: int
    name: str
    description: Optional[str]
    scope: int
    owner_id: Optional[int]
    created_at: datetime

    model_config = {"from_attributes": True}


class TableDetailOut(TableOut):
    columns: list[ColumnOut]


class TableUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
