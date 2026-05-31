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
        job_out = EtlJobOut.model_validate(job)
        job_out.steps = [EtlStepOut.model_validate(s) for s in steps]
        result.append(job_out)
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
