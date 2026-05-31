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
