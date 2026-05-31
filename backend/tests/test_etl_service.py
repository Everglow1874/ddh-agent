import os
import pytest
from pathlib import Path
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.database import Base
from app.models.etl import EtlJob, EtlStep
from app.models.project import Project
from app.services.etl_service import (
    write_sql_file, write_plan_md, create_etl_job, create_etl_step, get_projects_dir
)


@pytest.fixture(scope="function")
def db():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()
    Base.metadata.drop_all(engine)
    engine.dispose()


def test_write_sql_file(tmp_path):
    path = write_sql_file(project_id=1, step_order=1, step_name="load_orders", sql="SELECT 1;", base_dir=tmp_path)
    assert Path(path).exists()
    assert "SELECT 1;" in Path(path).read_text(encoding="utf-8")
    assert "step1" in Path(path).name


def test_write_plan_md(tmp_path):
    steps = [
        {"step_order": 1, "step_name": "load", "description": "Load data", "is_temp_table": True, "output_table": "tmp_load"},
        {"step_order": 2, "step_name": "agg", "description": "Aggregate", "is_temp_table": False, "output_table": "result"},
    ]
    path = write_plan_md(
        project_id=1,
        target_table="result",
        requirement="Count orders",
        steps=steps,
        base_dir=tmp_path,
    )
    content = Path(path).read_text(encoding="utf-8")
    assert "result" in content
    assert "Count orders" in content
    assert "load" in content


def test_create_etl_job_and_steps(db, tmp_path):
    project = Project(name="P", owner_id=1)
    db.add(project)
    db.flush()

    sql_path = write_sql_file(
        project_id=project.id, step_order=1, step_name="step1",
        sql="INSERT INTO t SELECT 1;", base_dir=tmp_path
    )
    plan_path = write_plan_md(
        project_id=project.id, target_table="t",
        requirement="test", steps=[], base_dir=tmp_path
    )

    job = create_etl_job(
        db=db,
        project_id=project.id,
        target_table="t",
        target_schema=[{"name": "id", "type": "INT"}],
        plan_md_path=plan_path,
    )
    step = create_etl_step(
        db=db,
        job_id=job.id,
        step_order=1,
        step_name="step1",
        is_temp_table=False,
        sql_file_path=sql_path,
    )

    assert job.id is not None
    assert step.job_id == job.id
    assert step.sql_file_path == sql_path


def test_get_projects_dir_returns_path():
    d = get_projects_dir()
    assert isinstance(d, Path)
