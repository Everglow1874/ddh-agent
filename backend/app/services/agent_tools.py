import json
from typing import Callable
from sqlalchemy.orm import Session
from app.models.source_table import SourceTable, TableColumn
from app.models.project import ProjectTable
from app.models.conversation import Conversation

AGENT_TOOLS = [
    {
        "name": "list_project_tables",
        "description": "Get all source tables selected for this ETL project.",
        "parameters": {
            "type": "object",
            "properties": {
                "project_id": {"type": "integer", "description": "The project ID"}
            },
            "required": ["project_id"],
        },
    },
    {
        "name": "get_table_schema",
        "description": "Get column definitions (name, type, comment) for a source table.",
        "parameters": {
            "type": "object",
            "properties": {
                "table_id": {"type": "integer", "description": "The table ID"}
            },
            "required": ["table_id"],
        },
    },
    {
        "name": "propose_schema",
        "description": (
            "Propose the target table structure to the user for confirmation. "
            "Call this when you have determined the output table columns from the requirements."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "target_table": {"type": "string", "description": "Name of the output target table"},
                "columns": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "type": {"type": "string"},
                            "comment": {"type": "string"},
                        },
                        "required": ["name", "type"],
                    },
                },
            },
            "required": ["target_table", "columns"],
        },
    },
    {
        "name": "propose_etl_steps",
        "description": (
            "Propose the ETL execution plan to the user for confirmation. "
            "Call this after schema has been confirmed."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "steps": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "step_order": {"type": "integer"},
                            "step_name": {"type": "string"},
                            "description": {"type": "string"},
                            "is_temp_table": {"type": "boolean"},
                            "output_table": {"type": "string"},
                        },
                        "required": ["step_order", "step_name", "description", "is_temp_table", "output_table"],
                    },
                }
            },
            "required": ["steps"],
        },
    },
    {
        "name": "generate_sql",
        "description": (
            "Generate GaussDB SQL for one ETL step. Call once per confirmed step."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "step_order": {"type": "integer"},
                "step_name": {"type": "string"},
                "is_temp_table": {"type": "boolean"},
                "sql": {"type": "string", "description": "Complete GaussDB SQL for this step"},
            },
            "required": ["step_order", "step_name", "is_temp_table", "sql"],
        },
    },
]

_TOOLS_BY_STATE: dict[int, list[str]] = {
    1: ["list_project_tables", "get_table_schema", "propose_schema"],
    3: ["propose_etl_steps"],
    4: ["generate_sql"],
}


def get_tools_for_state(state: int) -> list[dict]:
    names = _TOOLS_BY_STATE.get(state, [])
    return [t for t in AGENT_TOOLS if t["name"] in names]


def execute_tool(
    name: str,
    input_data: dict,
    db: Session,
    conversation: Conversation,
    emit: Callable[[dict], None],
) -> dict:
    if name == "list_project_tables":
        project_id = input_data["project_id"]
        rows = db.query(ProjectTable).filter(ProjectTable.project_id == project_id).all()
        table_ids = [r.table_id for r in rows]
        tables = db.query(SourceTable).filter(SourceTable.id.in_(table_ids)).all()
        return {"tables": [{"id": t.id, "name": t.name, "scope": t.scope} for t in tables]}

    if name == "get_table_schema":
        table_id = input_data["table_id"]
        table = db.query(SourceTable).filter(SourceTable.id == table_id).first()
        if table is None:
            return {"error": f"Table {table_id} not found"}
        columns = (
            db.query(TableColumn)
            .filter(TableColumn.table_id == table_id)
            .order_by(TableColumn.sort_order)
            .all()
        )
        return {
            "name": table.name,
            "columns": [
                {"column_name": c.column_name, "data_type": c.data_type, "comment": c.comment}
                for c in columns
            ],
        }

    if name == "propose_schema":
        conversation.state = 2
        db.commit()
        emit({
            "type": "schema_proposal",
            "target_table": input_data["target_table"],
            "columns": input_data["columns"],
        })
        return {"status": "proposal_sent"}

    if name == "propose_etl_steps":
        conversation.state = 3
        db.commit()
        emit({
            "type": "steps_proposal",
            "steps": input_data["steps"],
        })
        return {"status": "proposal_sent"}

    if name == "generate_sql":
        from app.services.etl_service import write_sql_file
        file_path = write_sql_file(
            project_id=conversation.project_id,
            step_order=input_data["step_order"],
            step_name=input_data["step_name"],
            sql=input_data["sql"],
        )
        emit({
            "type": "step_generated",
            "step_order": input_data["step_order"],
            "step_name": input_data["step_name"],
            "sql": input_data["sql"],
            "file": file_path,
        })
        return {
            "status": "sql_saved",
            "step_order": input_data["step_order"],
            "file_path": file_path,
            "is_temp_table": input_data["is_temp_table"],
            "step_name": input_data["step_name"],
        }

    return {"error": f"Unknown tool: {name}"}
