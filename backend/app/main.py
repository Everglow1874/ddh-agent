from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import auth
from app.config import settings
import logging

logger = logging.getLogger(__name__)

app = FastAPI(title="DDH Agent API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api")


@app.on_event("startup")
async def startup_check():
    if not settings.secret_key:
        logger.warning(
            "WARNING: secret_key is empty. Set a strong secret_key in config.yaml before deploying to production."
        )
