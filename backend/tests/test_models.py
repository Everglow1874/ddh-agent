import pytest
from sqlalchemy import create_engine, inspect
from sqlalchemy.orm import sessionmaker
from app.database import Base
from app.models import user, source_table, project, conversation, etl


@pytest.fixture(scope="module")
def engine():
    eng = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(eng)
    yield eng
    Base.metadata.drop_all(eng)


def test_all_tables_created(engine):
    inspector = inspect(engine)
    tables = inspector.get_table_names()
    expected = {
        "users", "source_tables", "table_columns",
        "projects", "project_tables",
        "conversations", "messages",
        "etl_jobs", "etl_steps",
    }
    assert expected.issubset(set(tables))


def test_user_columns(engine):
    inspector = inspect(engine)
    cols = {c["name"] for c in inspector.get_columns("users")}
    assert {"id", "username", "email", "password_hash", "role", "created_at"}.issubset(cols)


def test_source_table_columns(engine):
    inspector = inspect(engine)
    cols = {c["name"] for c in inspector.get_columns("source_tables")}
    assert {"id", "name", "description", "scope", "owner_id", "created_at"}.issubset(cols)
