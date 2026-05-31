import json
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.database import Base
from app.models.source_table import SourceTable, TableColumn
from app.models.project import Project, ProjectTable
from app.models.conversation import Conversation
from app.services.agent_tools import execute_tool, get_tools_for_state, AGENT_TOOLS


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


@pytest.fixture
def setup_project_with_table(db):
    table = SourceTable(name="dw_order", scope=1)
    db.add(table)
    db.flush()
    db.add(TableColumn(table_id=table.id, column_name="user_id", data_type="VARCHAR(64)", comment="用户ID", sort_order=0))
    db.add(TableColumn(table_id=table.id, column_name="amount", data_type="DECIMAL(18,2)", comment="金额", sort_order=1))
    project = Project(name="Test", owner_id=1)
    db.add(project)
    db.flush()
    db.add(ProjectTable(project_id=project.id, table_id=table.id))
    conv = Conversation(project_id=project.id, state=1)
    db.add(conv)
    db.commit()
    return {"table": table, "project": project, "conv": conv}


def test_list_project_tables(db, setup_project_with_table):
    conv = setup_project_with_table["conv"]
    emitted = []
    result = execute_tool("list_project_tables", {"project_id": conv.project_id}, db, conv, emitted.append)
    assert "tables" in result
    assert len(result["tables"]) == 1
    assert result["tables"][0]["name"] == "dw_order"


def test_get_table_schema(db, setup_project_with_table):
    table = setup_project_with_table["table"]
    conv = setup_project_with_table["conv"]
    emitted = []
    result = execute_tool("get_table_schema", {"table_id": table.id}, db, conv, emitted.append)
    assert result["name"] == "dw_order"
    assert len(result["columns"]) == 2
    assert result["columns"][0]["column_name"] == "user_id"


def test_propose_schema_emits_event(db, setup_project_with_table):
    conv = setup_project_with_table["conv"]
    emitted = []
    columns = [{"name": "user_id", "type": "VARCHAR(64)", "comment": "用户ID"}]
    result = execute_tool("propose_schema", {"target_table": "result_table", "columns": columns}, db, conv, emitted.append)
    assert result["status"] == "proposal_sent"
    assert len(emitted) == 1
    assert emitted[0]["type"] == "schema_proposal"
    assert emitted[0]["target_table"] == "result_table"
    db.refresh(conv)
    assert conv.state == 2


def test_get_tools_for_state_1():
    tools = get_tools_for_state(1)
    names = {t["name"] for t in tools}
    assert "list_project_tables" in names
    assert "get_table_schema" in names
    assert "propose_schema" in names
    assert "generate_sql" not in names


def test_get_tools_for_state_4():
    tools = get_tools_for_state(4)
    names = {t["name"] for t in tools}
    assert "generate_sql" in names
    assert "propose_schema" not in names
