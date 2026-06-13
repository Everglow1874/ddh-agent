from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.deps import get_db, get_current_user
from app.models.project import ProjectTable
from app.models.source_table import SourceTable, TableColumn
from app.models.user import User
from app.schemas.project import ProjectCreate, ProjectUpdate, ProjectOut, TableAssociateIn, AssociateResult
from app.services.project_service import (
    create_project, list_user_projects, get_project,
    update_project, delete_project, associate_tables, remove_table_association,
)

router = APIRouter(prefix="/projects", tags=["projects"])


def _get_owned_project(project_id: int, db: Session, current_user: User):
    project = get_project(db, project_id)
    if project is None:
        raise HTTPException(status_code=404, detail="Project not found")
    if project.owner_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not your project")
    return project


@router.post("", response_model=ProjectOut, status_code=201)
def create(
    body: ProjectCreate,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    return create_project(db, body.name, body.description, current_user.id)


@router.get("", response_model=list[ProjectOut])
def list_projects(
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    return list_user_projects(db, current_user.id)


@router.get("/{project_id}", response_model=ProjectOut)
def get(
    project_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    return _get_owned_project(project_id, db, current_user)


@router.put("/{project_id}", response_model=ProjectOut)
def update(
    project_id: int,
    body: ProjectUpdate,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    project = _get_owned_project(project_id, db, current_user)
    return update_project(db, project, name=body.name, description=body.description, status=body.status)


@router.delete("/{project_id}", status_code=204)
def delete(
    project_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    project = _get_owned_project(project_id, db, current_user)
    delete_project(db, project)


@router.post("/{project_id}/tables", response_model=AssociateResult)
def add_tables(
    project_id: int,
    body: TableAssociateIn,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    _get_owned_project(project_id, db, current_user)
    added = associate_tables(db, project_id, body.table_ids)
    return AssociateResult(associated=added)


@router.delete("/{project_id}/tables/{table_id}", status_code=204)
def remove_table(
    project_id: int,
    table_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    _get_owned_project(project_id, db, current_user)
    removed = remove_table_association(db, project_id, table_id)
    if not removed:
        raise HTTPException(status_code=404, detail="Association not found")


@router.get("/{project_id}/tables-with-details")
def get_project_tables_with_details(
    project_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    _get_owned_project(project_id, db, current_user)
    rows = db.query(ProjectTable).filter(ProjectTable.project_id == project_id).all()
    result = []
    for row in rows:
        tbl = db.query(SourceTable).filter(SourceTable.id == row.table_id).first()
        if tbl is None:
            continue
        cols = db.query(TableColumn).filter(TableColumn.table_id == tbl.id).order_by(TableColumn.sort_order).all()
        result.append({
            "id": tbl.id,
            "name": tbl.name,
            "description": tbl.description,
            "columns": [{"id": c.id, "column_name": c.column_name, "data_type": c.data_type, "comment": c.comment, "sort_order": c.sort_order} for c in cols],
        })
    return result
