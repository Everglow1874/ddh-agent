from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import auth, tables, projects, conversations, jobs, admin
from app.config import settings
from app.database import Base, engine
import app.models  # noqa: F401 — registers all models with Base.metadata
import logging

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not settings.secret_key:
        logger.warning(
            "WARNING: secret_key is empty. Set a strong secret_key in config.yaml before deploying to production."
        )
    # Create any missing tables on startup. Safe to run repeatedly —
    # create_all only issues CREATE TABLE for tables that don't exist.
    # Wrapped defensively so a missing/unreachable DB (e.g. in CI where tests
    # override the session with in-memory SQLite) does not crash app startup.
    try:
        Base.metadata.create_all(engine)
    except Exception as exc:  # pragma: no cover
        logger.warning("Skipping startup table creation: %s", exc)
    yield


app = FastAPI(title="DDH Agent API", version="0.1.0", lifespan=lifespan)

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
app.include_router(conversations.router, prefix="/api")
app.include_router(jobs.router, prefix="/api")
app.include_router(admin.router, prefix="/api")
