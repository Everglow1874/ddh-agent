from pathlib import Path
import yaml
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    secret_key: str = ""
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
