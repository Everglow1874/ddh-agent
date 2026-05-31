# DDH Agent Backend Foundation - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the FastAPI backend covering project setup, MySQL data models, JWT auth, source table management (CSV import), and project management.

**Architecture:** FastAPI with synchronous SQLAlchemy 2.x ORM against MySQL. Business logic isolated in a service layer; HTTP handling in routers. Tests run against SQLite in-memory (no MySQL required for CI).

**Tech Stack:** Python 3.11+, FastAPI 0.115, SQLAlchemy 2.x, PyMySQL, python-jose (JWT), passlib[bcrypt], pandas (CSV parse), pytest, httpx

**Spec reference:** `docs/superpowers/specs/2026-05-31-ddh-agent-platform-design.md`

---

## File Map

```
ddh-agent/
+-- backend/
|   +-- app/
|   |   +-- __init__.py
|   |   +-- main.py              # FastAPI app, CORS, router mounts
|   |   +-- config.py            # Settings loaded from config.yaml
|   |   +-- database.py          # SQLAlchemy engine, session, Base
|   |   +-- deps.py              # get_db, get_current_user dependencies
|   |   +-- models/
|   |   |   +-- __init__.py
|   |   |   +-- user.py          # User model
|   |   |   +-- source_table.py  # SourceTable + TableColumn models
|   |   |   +-- project.py       # Project + ProjectTable models
|   |   |   +-- conversation.py  # Conversation + Message models
|   |   |   +-- etl.py           # EtlJob + EtlStep models
|   |   +-- schemas/
|   |   |   +-- __init__.py
|   |   |   +-- user.py          # Pydantic request/response schemas
|   |   |   +-- source_table.py
|   |   |   +-- project.py
|   |   +-- routers/
|   |   |   +-- __init__.py
|   |   |   +-- auth.py          # POST /auth/register, /auth/login
|   |   |   +-- tables.py        # CRUD + CSV import for source tables
|   |   |   +-- projects.py      # Project CRUD + table associations
|   |   +-- services/
|   |       +-- __init__.py
|   |       +-- auth_service.py  # password hashing, JWT
|   |       +-- table_service.py # CSV parsing, table CRUD logic
|   |       +-- project_service.py
|   +-- tests/
|   |   +-- conftest.py          # Test DB, client fixture, auth_headers fixture
|   |   +-- test_auth.py
|   |   +-- test_tables.py
|   |   +-- test_projects.py
|   +-- config.yaml
|   +-- requirements.txt
|   +-- .gitignore
+-- docs/  (existing)
```

---

## Task 1: Project Setup

**Files:**
- Create: `backend/requirements.txt`
- Create: `backend/config.yaml`
- Create: `backend/.gitignore`
- Create: `backend/app/__init__.py` (empty)
- Create: `backend/app/models/__init__.py` (empty)
- Create: `backend/app/schemas/__init__.py` (empty)
- Create: `backend/app/routers/__init__.py` (empty)
- Create: `backend/app/services/__init__.py` (empty)
- Create: `backend/tests/__init__.py` (empty)

- [ ] **Step 1: Create directory structure**

```bash
cd d:/learning/project/pythonProject/ddh-agent
mkdir -p backend/app/models backend/app/schemas backend/app/routers backend/app/services backend/tests
```

- [ ] **Step 2: Create requirements.txt**

```
# backend/requirements.txt
fastapi==0.115.5
uvicorn[standard]==0.32.1
sqlalchemy==2.0.36
pymysql==1.1.1
cryptography==43.0.3
pydantic==2.9.2
pydantic-settings==2.6.1
python-jose[cryptography]==3.3.0
passlib[bcrypt]==1.7.4
python-multipart==0.0.17
pandas==2.2.3
pyyaml==6.0.2
pytest==8.3.3
httpx==0.27.2
pytest-mock==3.14.0
```

- [ ] **Step 3: Create config.yaml**

```yaml
# backend/config.yaml
app:
  secret_key: "change-me-in-production-use-a-long-random-string"
  algorithm: "HS256"
  access_token_expire_minutes: 1440

database:
  url: "mysql+pymysql://root:password@localhost:3306/ddh_agent"

llm:
  provider: claude
  claude:
    api_key: ""
    model: claude-sonnet-4-6
  qwen:
    api_key: ""
    model: qwen-max

files:
  projects_dir: "./projects"
```

- [ ] **Step 4: Create .gitignore**

```
# backend/.gitignore
__pycache__/
*.pyc
.venv/
venv/
.env
projects/
*.db
```

- [ ] **Step 5: Create all empty __init__.py files**

```bash
touch backend/app/__init__.py
touch backend/app/models/__init__.py
touch backend/app/schemas/__init__.py
touch backend/app/routers/__init__.py
touch backend/app/services/__init__.py
touch backend/tests/__init__.py
```

- [ ] **Step 6: Install dependencies**

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate   # Windows
pip install -r requirements.txt
```

Expected: all packages install without errors.

- [ ] **Step 7: Commit**

```bash
git add backend/
git commit -m "feat: scaffold backend directory structure and dependencies"
```

---

## Task 2: Config & Database

**Files:**
- Create: `backend/app/config.py`
- Create: `backend/app/database.py`

- [ ] **Step 1: Write failing test**

Create `backend/tests/test_config.py`:

```python
from app.config import settings

def test_settings_has_required_fields():
    assert settings.secret_key
    assert settings.algorithm == "HS256"
    assert settings.database_url
    assert settings.projects_dir

def test_settings_database_url_format():
    assert "://" in settings.database_url
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend
.venv\Scripts\activate
pytest tests/test_config.py -v
```

Expected: `ImportError: cannot import name 'settings'`

- [ ] **Step 3: Create app/config.py**

```python
from pathlib import Path
import yaml
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    secret_key: str = "change-me"
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 1440
    database_url: str = "sqlite:///./test.db"
    projects_dir: str = "./projects"
    llm_provider: str = "claude"
    claude_api_key: str = ""
    claude_model: str = "claude-sonnet-4-6"
    qwen_api_key: str = ""
    qwen_model: str = "qwen-max"


def _load_from_yaml() -> Settings:
    config_path = Path(__file__).parent.parent / "config.yaml"
    if not config_path.exists():
        return Settings()
    with open(config_path, encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    return Settings(
        secret_key=cfg["app"]["secret_key"],
        algorithm=cfg["app"]["algorithm"],
        access_token_expire_minutes=cfg["app"]["access_token_expire_minutes"],
        database_url=cfg["database"]["url"],
        projects_dir=cfg.get("files", {}).get("projects_dir", "./projects"),
        llm_provider=cfg["llm"]["provider"],
        claude_api_key=cfg["llm"]["claude"]["api_key"],
        claude_model=cfg["llm"]["claude"]["model"],
        qwen_api_key=cfg["llm"]["qwen"]["api_key"],
        qwen_model=cfg["llm"]["qwen"]["model"],
    )


settings = _load_from_yaml()
```

- [ ] **Step 4: Create app/database.py**

```python
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase
from app.config import settings


class Base(DeclarativeBase):
    pass


engine = create_engine(
    settings.database_url,
    pool_pre_ping=True,
    connect_args={"check_same_thread": False} if "sqlite" in settings.database_url else {},
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
```

- [ ] **Step 5: Run test to verify it passes**

```bash
pytest tests/test_config.py -v
```

Expected: `2 passed`

- [ ] **Step 6: Commit**

```bash
git add backend/app/config.py backend/app/database.py backend/tests/test_config.py
git commit -m "feat: add config loader and database session setup"
```

---

## Task 3: SQLAlchemy Models

**Files:**
- Create: `backend/app/models/user.py`
- Create: `backend/app/models/source_table.py`
- Create: `backend/app/models/project.py`
- Create: `backend/app/models/conversation.py`
- Create: `backend/app/models/etl.py`
- Modify: `backend/app/models/__init__.py`

- [ ] **Step 1: Write failing test**

Create `backend/tests/test_models.py`:

```python
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
pytest tests/test_models.py -v
```

Expected: `ImportError: cannot import name 'user' from 'app.models'`

- [ ] **Step 3: Create app/models/user.py**

```python
from datetime import datetime
from sqlalchemy import BigInteger, String, SmallInteger, DateTime
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    username: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    email: Mapped[str] = mapped_column(String(128), unique=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(256), nullable=False)
    role: Mapped[int] = mapped_column(SmallInteger, default=2)  # 1=admin 2=member
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
```

- [ ] **Step 4: Create app/models/source_table.py**

```python
from datetime import datetime
from typing import Optional
from sqlalchemy import BigInteger, String, SmallInteger, DateTime, Text, Integer
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base


class SourceTable(Base):
    __tablename__ = "source_tables"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    scope: Mapped[int] = mapped_column(SmallInteger, default=2)  # 1=public 2=private
    owner_id: Mapped[Optional[int]] = mapped_column(BigInteger, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class TableColumn(Base):
    __tablename__ = "table_columns"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    table_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    column_name: Mapped[str] = mapped_column(String(128), nullable=False)
    data_type: Mapped[str] = mapped_column(String(64), nullable=False)
    comment: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=0)
```

- [ ] **Step 5: Create app/models/project.py**

```python
from datetime import datetime
from sqlalchemy import BigInteger, String, SmallInteger, DateTime, Text
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base


class Project(Base):
    __tablename__ = "projects"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(256), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=True)
    owner_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    status: Mapped[int] = mapped_column(SmallInteger, default=1)  # 1=draft 2=in_progress 3=done
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class ProjectTable(Base):
    __tablename__ = "project_tables"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    project_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    table_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
```

- [ ] **Step 6: Create app/models/conversation.py**

```python
from datetime import datetime
from sqlalchemy import BigInteger, SmallInteger, DateTime, Text, String
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base


class Conversation(Base):
    __tablename__ = "conversations"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    project_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    state: Mapped[int] = mapped_column(SmallInteger, default=1)
    # 1=gathering 2=schema_confirm 3=steps_confirm 4=generating 5=done
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class Message(Base):
    __tablename__ = "messages"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    conversation_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    role: Mapped[str] = mapped_column(String(20), nullable=False)  # user/assistant/tool
    content: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
```

- [ ] **Step 7: Create app/models/etl.py**

```python
from datetime import datetime
from typing import Optional
from sqlalchemy import BigInteger, SmallInteger, DateTime, Text, String, Integer, JSON
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base


class EtlJob(Base):
    __tablename__ = "etl_jobs"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    project_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    target_table: Mapped[str] = mapped_column(String(128), nullable=False)
    target_schema: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    plan_md_path: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class EtlStep(Base):
    __tablename__ = "etl_steps"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    job_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    step_order: Mapped[int] = mapped_column(Integer, nullable=False)
    step_name: Mapped[str] = mapped_column(String(256), nullable=False)
    is_temp_table: Mapped[int] = mapped_column(SmallInteger, default=0)  # 0=final 1=temp
    sql_file_path: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
```

- [ ] **Step 8: Update app/models/__init__.py**

```python
from app.models.user import User
from app.models.source_table import SourceTable, TableColumn
from app.models.project import Project, ProjectTable
from app.models.conversation import Conversation, Message
from app.models.etl import EtlJob, EtlStep

__all__ = [
    "User", "SourceTable", "TableColumn",
    "Project", "ProjectTable",
    "Conversation", "Message",
    "EtlJob", "EtlStep",
]
```

- [ ] **Step 9: Run tests to verify they pass**

```bash
pytest tests/test_models.py -v
```

Expected: `3 passed`

- [ ] **Step 10: Commit**

```bash
git add backend/app/models/
git commit -m "feat: add SQLAlchemy models for all 9 database tables"
```

---

## Task 4: Auth Service

**Files:**
- Create: `backend/app/services/auth_service.py`

- [ ] **Step 1: Write failing tests**

Create `backend/tests/test_auth_service.py`:

```python
import pytest
from app.services.auth_service import hash_password, verify_password, create_access_token, decode_access_token


def test_hash_password_returns_string():
    result = hash_password("mypassword")
    assert isinstance(result, str)
    assert result != "mypassword"


def test_verify_password_correct():
    hashed = hash_password("mypassword")
    assert verify_password("mypassword", hashed) is True


def test_verify_password_wrong():
    hashed = hash_password("mypassword")
    assert verify_password("wrongpassword", hashed) is False


def test_create_and_decode_token():
    token = create_access_token(user_id=42)
    payload = decode_access_token(token)
    assert payload is not None
    assert payload["sub"] == 42


def test_decode_invalid_token_returns_none():
    result = decode_access_token("not.a.valid.token")
    assert result is None
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
pytest tests/test_auth_service.py -v
```

Expected: `ImportError: cannot import name 'hash_password'`

- [ ] **Step 3: Create app/services/auth_service.py**

```python
from datetime import datetime, timedelta, timezone
from typing import Optional
from jose import JWTError, jwt
from passlib.context import CryptContext
from app.config import settings

_pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    return _pwd_context.hash(password)


def verify_password(plain: str, hashed: str) -> bool:
    return _pwd_context.verify(plain, hashed)


def create_access_token(user_id: int) -> str:
    expire = datetime.now(timezone.utc) + timedelta(
        minutes=settings.access_token_expire_minutes
    )
    payload = {"sub": user_id, "exp": expire}
    return jwt.encode(payload, settings.secret_key, algorithm=settings.algorithm)


def decode_access_token(token: str) -> Optional[dict]:
    try:
        return jwt.decode(token, settings.secret_key, algorithms=[settings.algorithm])
    except JWTError:
        return None
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
pytest tests/test_auth_service.py -v
```

Expected: `5 passed`

- [ ] **Step 5: Commit**

```bash
git add backend/app/services/auth_service.py backend/tests/test_auth_service.py
git commit -m "feat: add password hashing and JWT service"
```

---

## Task 5: Auth Router + App Bootstrap

**Files:**
- Create: `backend/app/deps.py`
- Create: `backend/app/schemas/user.py`
- Create: `backend/app/routers/auth.py`
- Create: `backend/app/main.py`
- Create: `backend/tests/conftest.py`

- [ ] **Step 1: Write failing tests**

Create `backend/tests/test_auth.py`:

```python
def test_register_success(client):
    resp = client.post("/api/auth/register", json={
        "username": "alice",
        "email": "alice@example.com",
        "password": "password123"
    })
    assert resp.status_code == 201
    data = resp.json()
    assert data["username"] == "alice"
    assert "password" not in data
    assert "password_hash" not in data


def test_register_duplicate_username(client):
    payload = {"username": "bob", "email": "bob@example.com", "password": "pass123"}
    client.post("/api/auth/register", json=payload)
    resp = client.post("/api/auth/register", json={
        "username": "bob",
        "email": "bob2@example.com",
        "password": "pass123"
    })
    assert resp.status_code == 400
    assert "username" in resp.json()["detail"].lower()


def test_register_duplicate_email(client):
    client.post("/api/auth/register", json={
        "username": "carol", "email": "carol@example.com", "password": "pass"
    })
    resp = client.post("/api/auth/register", json={
        "username": "carol2", "email": "carol@example.com", "password": "pass"
    })
    assert resp.status_code == 400
    assert "email" in resp.json()["detail"].lower()


def test_login_success(client):
    client.post("/api/auth/register", json={
        "username": "dave", "email": "dave@example.com", "password": "secret"
    })
    resp = client.post("/api/auth/login", json={
        "username": "dave", "password": "secret"
    })
    assert resp.status_code == 200
    assert "access_token" in resp.json()
    assert resp.json()["token_type"] == "bearer"


def test_login_wrong_password(client):
    client.post("/api/auth/register", json={
        "username": "eve", "email": "eve@example.com", "password": "correct"
    })
    resp = client.post("/api/auth/login", json={
        "username": "eve", "password": "wrong"
    })
    assert resp.status_code == 401


def test_login_nonexistent_user(client):
    resp = client.post("/api/auth/login", json={
        "username": "nobody", "password": "pass"
    })
    assert resp.status_code == 401
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
pytest tests/test_auth.py -v
```

Expected: `ERROR tests/test_auth.py - fixture 'client' not found`

- [ ] **Step 3: Create tests/conftest.py**

```python
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.main import app
from app.database import Base
from app.deps import get_db


@pytest.fixture(scope="function")
def client():
    engine = create_engine(
        "sqlite:///:memory:", connect_args={"check_same_thread": False}
    )
    Base.metadata.create_all(engine)
    Session = sessionmaker(autocommit=False, autoflush=False, bind=engine)

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
    Base.metadata.drop_all(engine)
    engine.dispose()


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
```

- [ ] **Step 4: Create app/schemas/user.py**

```python
from pydantic import BaseModel, EmailStr
from datetime import datetime


class UserRegister(BaseModel):
    username: str
    email: EmailStr
    password: str


class UserLogin(BaseModel):
    username: str
    password: str


class UserOut(BaseModel):
    id: int
    username: str
    email: str
    role: int
    created_at: datetime

    model_config = {"from_attributes": True}


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
```

- [ ] **Step 5: Create app/deps.py**

```python
from typing import Annotated
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from app.database import SessionLocal
from app.services.auth_service import decode_access_token
from app.models.user import User

_security = HTTPBearer()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials, Depends(_security)],
    db: Annotated[Session, Depends(get_db)],
) -> User:
    payload = decode_access_token(credentials.credentials)
    if payload is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
    user = db.query(User).filter(User.id == payload["sub"]).first()
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")
    return user
```

- [ ] **Step 6: Create app/routers/auth.py**

```python
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.deps import get_db
from app.models.user import User
from app.schemas.user import UserRegister, UserLogin, UserOut, TokenOut
from app.services.auth_service import hash_password, verify_password, create_access_token

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=UserOut, status_code=201)
def register(body: UserRegister, db: Annotated[Session, Depends(get_db)]):
    if db.query(User).filter(User.username == body.username).first():
        raise HTTPException(status_code=400, detail="Username already taken")
    if db.query(User).filter(User.email == body.email).first():
        raise HTTPException(status_code=400, detail="Email already registered")
    user = User(
        username=body.username,
        email=body.email,
        password_hash=hash_password(body.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.post("/login", response_model=TokenOut)
def login(body: UserLogin, db: Annotated[Session, Depends(get_db)]):
    user = db.query(User).filter(User.username == body.username).first()
    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")
    return TokenOut(access_token=create_access_token(user.id))
```

- [ ] **Step 7: Create app/main.py**

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import auth

app = FastAPI(title="DDH Agent API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api")
```

- [ ] **Step 8: Run tests to verify they pass**

```bash
pytest tests/test_auth.py -v
```

Expected: `6 passed`

- [ ] **Step 9: Commit**

```bash
git add backend/app/deps.py backend/app/schemas/user.py backend/app/routers/auth.py backend/app/main.py backend/tests/conftest.py backend/tests/test_auth.py
git commit -m "feat: add JWT auth endpoints (register + login)"
```

---

## Task 6: Source Table Service (CSV Import)

**Files:**
- Create: `backend/app/schemas/source_table.py`
- Create: `backend/app/services/table_service.py`

CSV format expected (each row = one column definition):

```csv
column_name,data_type,comment
user_id,VARCHAR(64),用户ID
order_date,DATE,订单日期
amount,DECIMAL(18,2),订单金额
```

- [ ] **Step 1: Write failing unit tests**

Create `backend/tests/test_table_service.py`:

```python
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
pytest tests/test_table_service.py -v
```

Expected: `ImportError: cannot import name 'parse_csv'`

- [ ] **Step 3: Create app/services/table_service.py**

```python
import io
from typing import Optional
import pandas as pd
from sqlalchemy.orm import Session
from app.models.source_table import SourceTable, TableColumn


def parse_csv(file: io.BytesIO) -> list[dict]:
    df = pd.read_csv(file)
    required = {"column_name", "data_type"}
    missing = required - set(df.columns.str.strip())
    if missing:
        raise ValueError(f"CSV missing required columns: {missing}")
    df.columns = df.columns.str.strip()
    rows = []
    for i, row in df.iterrows():
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
    db.flush()  # get table.id without committing
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
pytest tests/test_table_service.py -v
```

Expected: `5 passed`

- [ ] **Step 5: Create app/schemas/source_table.py**

```python
from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class ColumnOut(BaseModel):
    id: int
    column_name: str
    data_type: str
    comment: Optional[str]
    sort_order: int

    model_config = {"from_attributes": True}


class TableOut(BaseModel):
    id: int
    name: str
    description: Optional[str]
    scope: int
    owner_id: Optional[int]
    created_at: datetime

    model_config = {"from_attributes": True}


class TableDetailOut(TableOut):
    columns: list[ColumnOut]


class TableUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
```

- [ ] **Step 6: Commit**

```bash
git add backend/app/services/table_service.py backend/app/schemas/source_table.py backend/tests/test_table_service.py
git commit -m "feat: add CSV parsing and source table service"
```

---

## Task 7: Tables Router

**Files:**
- Create: `backend/app/routers/tables.py`
- Modify: `backend/app/main.py`

- [ ] **Step 1: Write failing API tests**

Create `backend/tests/test_tables.py`:

```python
import io


VALID_CSV_BYTES = (
    b"column_name,data_type,comment\n"
    b"user_id,VARCHAR(64),\xe7\x94\xa8\xe6\x88\xb7ID\n"
    b"amount,DECIMAL(18,2),\xe9\x87\x91\xe9\xa2\x9d\n"
)


def test_import_csv_public_table(client, auth_headers):
    resp = client.post(
        "/api/tables/import",
        data={"scope": "1", "description": "订单表"},
        files={"file": ("dw_order.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data["name"] == "dw_order"
    assert data["scope"] == 1


def test_import_csv_private_table(client, auth_headers):
    resp = client.post(
        "/api/tables/import",
        data={"scope": "2"},
        files={"file": ("my_table.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    assert resp.json()["scope"] == 2


def test_import_csv_bad_format(client, auth_headers):
    bad_csv = b"wrong_col,data_type\nval,INT\n"
    resp = client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": ("bad.csv", io.BytesIO(bad_csv), "text/csv")},
        headers=auth_headers,
    )
    assert resp.status_code == 400
    assert "column_name" in resp.json()["detail"].lower()


def test_list_tables_requires_auth(client):
    resp = client.get("/api/tables")
    assert resp.status_code == 403


def test_list_tables_public(client, auth_headers):
    client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": ("t.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    resp = client.get("/api/tables?scope=public", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) >= 1


def test_get_table_detail(client, auth_headers):
    r = client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": ("detail.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    table_id = r.json()["id"]
    resp = client.get(f"/api/tables/{table_id}", headers=auth_headers)
    assert resp.status_code == 200
    assert "columns" in resp.json()
    assert len(resp.json()["columns"]) == 2


def test_delete_table(client, auth_headers):
    r = client.post(
        "/api/tables/import",
        data={"scope": "2"},
        files={"file": ("del.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    table_id = r.json()["id"]
    resp = client.delete(f"/api/tables/{table_id}", headers=auth_headers)
    assert resp.status_code == 204
    resp2 = client.get(f"/api/tables/{table_id}", headers=auth_headers)
    assert resp2.status_code == 404
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
pytest tests/test_tables.py -v
```

Expected: `FAILED - 404 Not Found` on `/api/tables/import`

- [ ] **Step 3: Create app/routers/tables.py**

```python
import io
from typing import Annotated, Optional
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
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
```

- [ ] **Step 4: Update app/main.py to add tables router**

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import auth, tables

app = FastAPI(title="DDH Agent API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api")
app.include_router(tables.router, prefix="/api")
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
pytest tests/test_tables.py -v
```

Expected: `7 passed`

- [ ] **Step 6: Commit**

```bash
git add backend/app/routers/tables.py backend/app/main.py backend/tests/test_tables.py
git commit -m "feat: add source table management endpoints with CSV import"
```

---

## Task 8: Projects Router

**Files:**
- Create: `backend/app/schemas/project.py`
- Create: `backend/app/services/project_service.py`
- Create: `backend/app/routers/projects.py`
- Modify: `backend/app/main.py`

- [ ] **Step 1: Write failing API tests**

Create `backend/tests/test_projects.py`:

```python
import io

VALID_CSV = (
    b"column_name,data_type,comment\n"
    b"id,BIGINT,\xe4\xb8\xbb\xe9\x94\xae\n"
)


def _import_table(client, auth_headers, name="src_table"):
    return client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": (f"{name}.csv", io.BytesIO(VALID_CSV), "text/csv")},
        headers=auth_headers,
    ).json()["id"]


def test_create_project(client, auth_headers):
    resp = client.post("/api/projects", json={
        "name": "用户月度统计",
        "description": "统计每月消费"
    }, headers=auth_headers)
    assert resp.status_code == 201
    data = resp.json()
    assert data["name"] == "用户月度统计"
    assert data["status"] == 1


def test_list_projects(client, auth_headers):
    client.post("/api/projects", json={"name": "P1"}, headers=auth_headers)
    client.post("/api/projects", json={"name": "P2"}, headers=auth_headers)
    resp = client.get("/api/projects", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) >= 2


def test_get_project(client, auth_headers):
    r = client.post("/api/projects", json={"name": "Detail"}, headers=auth_headers)
    pid = r.json()["id"]
    resp = client.get(f"/api/projects/{pid}", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["id"] == pid


def test_update_project(client, auth_headers):
    r = client.post("/api/projects", json={"name": "Old"}, headers=auth_headers)
    pid = r.json()["id"]
    resp = client.put(f"/api/projects/{pid}", json={"name": "New"}, headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["name"] == "New"


def test_associate_table_with_project(client, auth_headers):
    pid = client.post("/api/projects", json={"name": "Proj"}, headers=auth_headers).json()["id"]
    tid = _import_table(client, auth_headers)
    resp = client.post(f"/api/projects/{pid}/tables", json={"table_ids": [tid]}, headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["associated"] == 1


def test_remove_table_from_project(client, auth_headers):
    pid = client.post("/api/projects", json={"name": "Proj2"}, headers=auth_headers).json()["id"]
    tid = _import_table(client, auth_headers, "t2")
    client.post(f"/api/projects/{pid}/tables", json={"table_ids": [tid]}, headers=auth_headers)
    resp = client.delete(f"/api/projects/{pid}/tables/{tid}", headers=auth_headers)
    assert resp.status_code == 204


def test_delete_project(client, auth_headers):
    r = client.post("/api/projects", json={"name": "ToDelete"}, headers=auth_headers)
    pid = r.json()["id"]
    resp = client.delete(f"/api/projects/{pid}", headers=auth_headers)
    assert resp.status_code == 204
    resp2 = client.get(f"/api/projects/{pid}", headers=auth_headers)
    assert resp2.status_code == 404
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
pytest tests/test_projects.py -v
```

Expected: `FAILED - 404 Not Found` on `/api/projects`

- [ ] **Step 3: Create app/schemas/project.py**

```python
from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class ProjectCreate(BaseModel):
    name: str
    description: Optional[str] = None


class ProjectUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    status: Optional[int] = None


class ProjectOut(BaseModel):
    id: int
    name: str
    description: Optional[str]
    owner_id: int
    status: int
    created_at: datetime

    model_config = {"from_attributes": True}


class TableAssociateIn(BaseModel):
    table_ids: list[int]


class AssociateResult(BaseModel):
    associated: int
```

- [ ] **Step 4: Create app/services/project_service.py**

```python
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
```

- [ ] **Step 5: Create app/routers/projects.py**

```python
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.deps import get_db, get_current_user
from app.models.user import User
from app.schemas.project import ProjectCreate, ProjectUpdate, ProjectOut, TableAssociateIn, AssociateResult
from app.services.project_service import (
    create_project, list_user_projects, get_project,
    update_project, delete_project, associate_tables, remove_table_association,
)

router = APIRouter(prefix="/projects", tags=["projects"])


def _get_owned_project(project_id: int, db: Session, current_user: User):
    project = get_project(db, project_id)
    if project is None:
        raise HTTPException(status_code=404, detail="Project not found")
    if project.owner_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not your project")
    return project


@router.post("", response_model=ProjectOut, status_code=201)
def create(
    body: ProjectCreate,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    return create_project(db, body.name, body.description, current_user.id)


@router.get("", response_model=list[ProjectOut])
def list_projects(
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    return list_user_projects(db, current_user.id)


@router.get("/{project_id}", response_model=ProjectOut)
def get(
    project_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    return _get_owned_project(project_id, db, current_user)


@router.put("/{project_id}", response_model=ProjectOut)
def update(
    project_id: int,
    body: ProjectUpdate,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    project = _get_owned_project(project_id, db, current_user)
    return update_project(db, project, name=body.name, description=body.description, status=body.status)


@router.delete("/{project_id}", status_code=204)
def delete(
    project_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    project = _get_owned_project(project_id, db, current_user)
    delete_project(db, project)


@router.post("/{project_id}/tables", response_model=AssociateResult)
def add_tables(
    project_id: int,
    body: TableAssociateIn,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    _get_owned_project(project_id, db, current_user)
    added = associate_tables(db, project_id, body.table_ids)
    return AssociateResult(associated=added)


@router.delete("/{project_id}/tables/{table_id}", status_code=204)
def remove_table(
    project_id: int,
    table_id: int,
    db: Annotated[Session, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    _get_owned_project(project_id, db, current_user)
    removed = remove_table_association(db, project_id, table_id)
    if not removed:
        raise HTTPException(status_code=404, detail="Association not found")
```

- [ ] **Step 6: Update app/main.py**

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import auth, tables, projects

app = FastAPI(title="DDH Agent API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api")
app.include_router(tables.router, prefix="/api")
app.include_router(projects.router, prefix="/api")
```

- [ ] **Step 7: Run all tests**

```bash
pytest tests/ -v
```

Expected: all tests pass (target: 25+ tests, 0 failures)

- [ ] **Step 8: Run server locally to verify startup**

Prerequisites: MySQL running with a database named `ddh_agent`. Update `config.yaml` with your MySQL credentials, then:

```bash
cd backend
uvicorn app.main:app --reload --port 8000
```

Expected output:
```
INFO:     Uvicorn running on http://127.0.0.1:8000
INFO:     Application startup complete.
```

Visit `http://127.0.0.1:8000/docs` to confirm Swagger UI loads.

- [ ] **Step 9: Commit**

```bash
git add backend/app/schemas/project.py backend/app/services/project_service.py backend/app/routers/projects.py backend/app/main.py backend/tests/test_projects.py
git commit -m "feat: add project management endpoints with table associations"
```

---

## Self-Review Checklist

Spec coverage verified:

| Spec requirement | Task |
|------------------|------|
| users table with role TINYINT | Task 3 |
| source_tables, table_columns | Task 3 |
| projects, project_tables | Task 3 |
| conversations, messages, etl_jobs, etl_steps | Task 3 (models only — routers in Plan 2) |
| No FK constraints, enums as TINYINT/VARCHAR | Task 3 |
| JWT auth — register + login | Task 5 |
| CSV import (column_name, data_type, comment) | Task 6 & 7 |
| Source table CRUD + public/private scope | Task 7 |
| Project CRUD | Task 8 |
| Project-table associations (N:M) | Task 8 |
| config.yaml LLM config structure | Task 1 |

**Not covered in this plan (intentionally deferred to Plan 2):**
- Conversation endpoints
- Agent + SSE streaming
- ETL job generation
- LLM abstraction layer
- Admin config endpoint
