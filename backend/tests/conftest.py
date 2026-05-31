import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from app.main import app
from app.database import Base
from app.deps import get_db
import app.models as _models  # noqa: F401 — ensures all models are registered with Base.metadata


@pytest.fixture(scope="function")
def _engine():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    yield engine
    Base.metadata.drop_all(engine)
    engine.dispose()


@pytest.fixture(scope="function")
def client(_engine):
    Session = sessionmaker(autocommit=False, autoflush=False, bind=_engine)

    def override_get_db():
        db = Session()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture
def db_session(_engine):
    """A session bound to the same in-memory engine the client uses,
    so rows inserted here are visible to API requests and vice versa."""
    Session = sessionmaker(autocommit=False, autoflush=False, bind=_engine)
    session = Session()
    try:
        yield session
    finally:
        session.close()


@pytest.fixture
def auth_headers(client):
    client.post("/api/auth/register", json={
        "username": "testuser",
        "email": "testuser@example.com",
        "password": "testpass123",
    })
    resp = client.post("/api/auth/login", json={
        "username": "testuser",
        "password": "testpass123",
    })
    token = resp.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}
