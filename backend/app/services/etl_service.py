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
        "# ETL 执行计划\n",
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
