import io
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.database import Base
from app.models.source_table import SourceTable, TableColumn
from app.services.table_service import parse_csv, create_table_from_csv, get_table_with_columns


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


VALID_CSV = b"column_name,data_type,comment\nuser_id,VARCHAR(64),\xe7\x94\xa8\xe6\x88\xb7ID\norder_date,DATE,\xe8\xae\xa2\xe5\x8d\x95\xe6\x97\xa5\xe6\x9c\x9f\n"


def test_parse_csv_returns_list_of_dicts():
    rows = parse_csv(io.BytesIO(VALID_CSV))
    assert len(rows) == 2
    assert rows[0]["column_name"] == "user_id"
    assert rows[0]["data_type"] == "VARCHAR(64)"


def test_parse_csv_missing_column_name_raises():
    bad_csv = b"data_type,comment\nVARCHAR(64),test\n"
    with pytest.raises(ValueError, match="column_name"):
        parse_csv(io.BytesIO(bad_csv))


def test_parse_csv_missing_data_type_raises():
    bad_csv = b"column_name,comment\nuser_id,test\n"
    with pytest.raises(ValueError, match="data_type"):
        parse_csv(io.BytesIO(bad_csv))


def test_create_table_from_csv_saves_to_db(db):
    rows = parse_csv(io.BytesIO(VALID_CSV))
    table = create_table_from_csv(
        db, name="dw_order", description="订单表", scope=1, owner_id=None, columns=rows
    )
    assert table.id is not None
    assert table.name == "dw_order"
    cols = db.query(TableColumn).filter(TableColumn.table_id == table.id).all()
    assert len(cols) == 2
    assert cols[0].column_name == "user_id"


def test_get_table_with_columns_returns_dict(db):
    rows = parse_csv(io.BytesIO(VALID_CSV))
    table = create_table_from_csv(db, "test_table", None, 1, None, rows)
    result = get_table_with_columns(db, table.id)
    assert result is not None
    assert result["id"] == table.id
    assert len(result["columns"]) == 2
