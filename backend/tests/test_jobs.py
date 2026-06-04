from pathlib import Path
from sqlalchemy.orm import Session
from app.models.etl import EtlJob, EtlStep
from app.models.project import Project


def _create_job_in_db(db: Session, owner_id: int, sql_dir: Path) -> tuple:
    project = db.query(Project).filter(Project.owner_id == owner_id).first()
    sql_file = sql_dir / "step1_load.sql"
    sql_file.write_text("SELECT 1;", encoding="utf-8")
    plan_file = sql_dir / "plan.md"
    plan_file.write_text("# Plan", encoding="utf-8")
    job = EtlJob(
        project_id=project.id,
        target_table="result",
        target_schema=[{"name": "id", "type": "INT"}],
        plan_md_path=str(plan_file),
    )
    db.add(job)
    db.flush()
    step = EtlStep(
        job_id=job.id,
        step_order=1,
        step_name="load",
        is_temp_table=0,
        sql_file_path=str(sql_file),
    )
    db.add(step)
    db.commit()
    db.refresh(job)
    db.refresh(step)
    return job, step


def test_list_jobs_empty(client, auth_headers):
    pid = client.post("/api/projects", json={"name": "P"}, headers=auth_headers).json()["id"]
    resp = client.get(f"/api/projects/{pid}/jobs", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json() == []


def test_get_job_detail(client, auth_headers, db_session, tmp_path):
    pid = client.post("/api/projects", json={"name": "J"}, headers=auth_headers).json()["id"]
    job, _ = _create_job_in_db(db_session, owner_id=1, sql_dir=tmp_path)
    resp = client.get(f"/api/jobs/{job.id}", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["target_table"] == "result"
    assert len(data["steps"]) == 1
    assert data["steps"][0]["step_name"] == "load"


def test_list_jobs_with_job(client, auth_headers, db_session, tmp_path):
    pid = client.post("/api/projects", json={"name": "L"}, headers=auth_headers).json()["id"]
    _create_job_in_db(db_session, owner_id=1, sql_dir=tmp_path)
    resp = client.get(f"/api/projects/{pid}/jobs", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) == 1
    assert resp.json()[0]["steps"][0]["step_name"] == "load"


def test_get_sql_content(client, auth_headers, db_session, tmp_path):
    client.post("/api/projects", json={"name": "SQL"}, headers=auth_headers)
    job, step = _create_job_in_db(db_session, owner_id=1, sql_dir=tmp_path)
    resp = client.get(f"/api/jobs/{job.id}/steps/{step.id}/sql", headers=auth_headers)
    assert resp.status_code == 200
    assert "SELECT 1;" in resp.json()["sql"]


def test_download_job_zip(client, auth_headers, db_session, tmp_path):
    client.post("/api/projects", json={"name": "DL"}, headers=auth_headers)
    job, _ = _create_job_in_db(db_session, owner_id=1, sql_dir=tmp_path)
    resp = client.get(f"/api/jobs/{job.id}/download", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.headers["content-type"] == "application/zip"
    assert len(resp.content) > 0


def test_get_job_not_found(client, auth_headers):
    resp = client.get("/api/jobs/99999", headers=auth_headers)
    assert resp.status_code == 404


def test_admin_get_config(client, auth_headers):
    resp = client.get("/api/admin/config", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert "provider" in data
    assert "model" in data


def test_admin_update_config(client, auth_headers):
    resp = client.put("/api/admin/config",
                      json={"provider": "qwen", "model": "qwen-max"},
                      headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["provider"] == "qwen"
    assert resp.json()["model"] == "qwen-max"


def test_admin_update_config_invalid_provider(client, auth_headers):
    resp = client.put("/api/admin/config",
                      json={"provider": "invalid", "model": "x"},
                      headers=auth_headers)
    assert resp.status_code == 400


def test_admin_update_config_deepseek(client, auth_headers):
    resp = client.put("/api/admin/config",
                      json={"provider": "deepseek", "model": "deepseek-chat"},
                      headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["provider"] == "deepseek"
    assert resp.json()["model"] == "deepseek-chat"
