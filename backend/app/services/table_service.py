import csv
import io
from typing import Optional
from sqlalchemy.orm import Session
from app.models.source_table import SourceTable, TableColumn


def parse_csv(file: io.BytesIO) -> list[dict]:
    text = file.read().decode("utf-8")
    reader = csv.DictReader(io.StringIO(text))
    if reader.fieldnames is None:
        raise ValueError("CSV file is empty")
    fieldnames = [f.strip() for f in reader.fieldnames]
    required = {"column_name", "data_type"}
    missing = required - set(fieldnames)
    if missing:
        raise ValueError(f"CSV missing required columns: {', '.join(sorted(missing))}")
    rows = []
    for i, row in enumerate(reader):
        stripped = {k.strip(): v.strip() if v else v for k, v in row.items()}
        rows.append({
            "column_name": stripped["column_name"],
            "data_type": stripped["data_type"],
            "comment": stripped.get("comment") or None,
            "sort_order": i,
        })
    return rows


def create_table_from_csv(
    db: Session,
    name: str,
    description: Optional[str],
    scope: int,
    owner_id: Optional[int],
    columns: list[dict],
) -> SourceTable:
    table = SourceTable(name=name, description=description, scope=scope, owner_id=owner_id)
    db.add(table)
    db.flush()
    for col in columns:
        db.add(TableColumn(
            table_id=table.id,
            column_name=col["column_name"],
            data_type=col["data_type"],
            comment=col.get("comment"),
            sort_order=col.get("sort_order", 0),
        ))
    db.commit()
    db.refresh(table)
    return table


def get_table_with_columns(db: Session, table_id: int) -> Optional[dict]:
    table = db.query(SourceTable).filter(SourceTable.id == table_id).first()
    if table is None:
        return None
    columns = (
        db.query(TableColumn)
        .filter(TableColumn.table_id == table_id)
        .order_by(TableColumn.sort_order)
        .all()
    )
    return {
        "id": table.id,
        "name": table.name,
        "description": table.description,
        "scope": table.scope,
        "owner_id": table.owner_id,
        "created_at": table.created_at,
        "columns": [
            {
                "id": c.id,
                "column_name": c.column_name,
                "data_type": c.data_type,
                "comment": c.comment,
                "sort_order": c.sort_order,
            }
            for c in columns
        ],
    }
