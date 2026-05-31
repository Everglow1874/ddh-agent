import io
from typing import Optional
from sqlalchemy.orm import Session
from app.models.source_table import SourceTable, TableColumn


def parse_csv(file: io.BytesIO) -> list[dict]:
    """Parse a CSV with columns: column_name, data_type, comment.

    data_type values like DECIMAL(18,2) may contain commas and are NOT
    necessarily quoted in the file.  We resolve ambiguity by treating the
    first field as column_name, the last field as comment, and everything
    in between as data_type.  The header row is used solely to detect
    whether the required column_name / data_type names are present.
    """
    text = file.read()
    if isinstance(text, bytes):
        text = text.decode("utf-8")
    lines = [ln for ln in text.splitlines() if ln.strip()]
    if not lines:
        raise ValueError("CSV is empty")

    # Parse header to validate required columns exist
    header_parts = [h.strip() for h in lines[0].split(",")]
    if "column_name" not in header_parts or "data_type" not in header_parts:
        missing = sorted({"column_name", "data_type"} - set(header_parts))
        raise ValueError(f"CSV missing required columns: {', '.join(missing)}")

    has_comment = "comment" in header_parts

    rows = []
    for i, line in enumerate(lines[1:]):
        parts = [p.strip() for p in line.split(",")]
        if len(parts) < 2:
            continue
        column_name = parts[0]
        # If there are 3+ parts AND the header has a comment column,
        # treat the last part as comment and middle parts as data_type.
        if has_comment and len(parts) >= 3:
            comment_val = parts[-1] if parts[-1] else None
            data_type = ",".join(parts[1:-1])
        else:
            data_type = ",".join(parts[1:])
            comment_val = None
        rows.append({
            "column_name": column_name,
            "data_type": data_type,
            "comment": comment_val or None,
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
