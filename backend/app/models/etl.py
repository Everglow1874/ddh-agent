from datetime import datetime
from typing import Optional
from sqlalchemy import BigInteger, SmallInteger, DateTime, String, Integer, JSON
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
