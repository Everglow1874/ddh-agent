import io
from typing import Annotated, Optional
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from sqlalchemy.orm import Session
from app.deps import get_db, get_current_user
from app.models.source_table import SourceTable, TableColumn
from app.models.user import User
from app.schemas.source_table import TableOut, TableDetailOut, TableUpdate
from app.services.table_service import parse_csv, create_table_from_csv, get_table_with_columns

router = APIRouter(prefix="/tables", tags=["tables"])


@router.post("/import", response_model=TableOut, status_code=201)
def import_table(
    file: Annotated[UploadFile, File()],
    scope: Annotated[int, Form()],
    description: Annotated[Optional[str], Form()] = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    filename = file.filename or "unknown.csv"
    table_name = filename.rsplit(".", 1)[0]
    content = file.file.read()
    try:
        columns = parse_csv(io.BytesIO(content))
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    owner_id = None if scope == 1 else current_user.id
    return create_table_from_csv(db, table_name, description, scope, owner_id, columns)


@router.get("", response_model=list[TableOut])
def list_tables(
    scope: Optional[str] = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    query = db.query(SourceTable)
    if scope == "public":
        query = query.filter(SourceTable.scope == 1)
    elif scope == "private":
        query = query.filter(SourceTable.scope == 2, SourceTable.owner_id == current_user.id)
    else:
        query = query.filter(
            (SourceTable.scope == 1) |
            ((SourceTable.scope == 2) & (SourceTable.owner_id == current_user.id))
        )
    return query.order_by(SourceTable.created_at.desc()).all()


@router.get("/{table_id}", response_model=TableDetailOut)
def get_table(
    table_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    result = get_table_with_columns(db, table_id)
    if result is None:
        raise HTTPException(status_code=404, detail="Table not found")
    return result


@router.put("/{table_id}", response_model=TableOut)
def update_table(
    table_id: int,
    body: TableUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    table = db.query(SourceTable).filter(SourceTable.id == table_id).first()
    if table is None:
        raise HTTPException(status_code=404, detail="Table not found")
    if body.name is not None:
        table.name = body.name
    if body.description is not None:
        table.description = body.description
    db.commit()
    db.refresh(table)
    return table


@router.delete("/{table_id}", status_code=204)
def delete_table(
    table_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    table = db.query(SourceTable).filter(SourceTable.id == table_id).first()
    if table is None:
        raise HTTPException(status_code=404, detail="Table not found")
    db.query(TableColumn).filter(TableColumn.table_id == table_id).delete()
    db.delete(table)
    db.commit()
