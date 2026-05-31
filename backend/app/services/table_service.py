import io
from typing import Optional
import pandas as pd
from sqlalchemy.orm import Session
from app.models.source_table import SourceTable, TableColumn


def parse_csv(file: io.BytesIO) -> list[dict]:
    df = pd.read_csv(file)
    df.columns = df.columns.str.strip()
    required = {"column_name", "data_type"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"CSV missing required columns: {', '.join(sorted(missing))}")
    rows = []
    for i, (_, row) in enumerate(df.iterrows()):
        rows.append({
            "column_name": str(row["column_name"]).strip(),
            "data_type": str(row["data_type"]).strip(),
            "comment": str(row["comment"]).strip() if "comment" in df.columns and pd.notna(row.get("comment")) else None,
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
