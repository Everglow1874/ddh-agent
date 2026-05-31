from typing import Optional
from sqlalchemy.orm import Session
from app.models.project import Project, ProjectTable


def create_project(db: Session, name: str, description: Optional[str], owner_id: int) -> Project:
    project = Project(name=name, description=description, owner_id=owner_id)
    db.add(project)
    db.commit()
    db.refresh(project)
    return project


def list_user_projects(db: Session, owner_id: int) -> list[Project]:
    return (
        db.query(Project)
        .filter(Project.owner_id == owner_id)
        .order_by(Project.created_at.desc())
        .all()
    )


def get_project(db: Session, project_id: int) -> Optional[Project]:
    return db.query(Project).filter(Project.id == project_id).first()


def update_project(db: Session, project: Project, **kwargs) -> Project:
    for key, value in kwargs.items():
        if value is not None:
            setattr(project, key, value)
    db.commit()
    db.refresh(project)
    return project


def delete_project(db: Session, project: Project) -> None:
    db.query(ProjectTable).filter(ProjectTable.project_id == project.id).delete()
    db.delete(project)
    db.commit()


def associate_tables(db: Session, project_id: int, table_ids: list[int]) -> int:
    existing = {
        row.table_id
        for row in db.query(ProjectTable).filter(ProjectTable.project_id == project_id).all()
    }
    added = 0
    for tid in table_ids:
        if tid not in existing:
            db.add(ProjectTable(project_id=project_id, table_id=tid))
            added += 1
    db.commit()
    return added


def remove_table_association(db: Session, project_id: int, table_id: int) -> bool:
    row = db.query(ProjectTable).filter(
        ProjectTable.project_id == project_id,
        ProjectTable.table_id == table_id,
    ).first()
    if row is None:
        return False
    db.delete(row)
    db.commit()
    return True
