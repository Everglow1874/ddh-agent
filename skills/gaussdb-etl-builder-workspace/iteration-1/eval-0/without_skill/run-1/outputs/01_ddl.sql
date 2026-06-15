-- =============================================================
-- 01_ddl.sql
-- 创建暂存表与目标报表表（GaussDB / PostgreSQL 兼容）
-- 可重复执行：先 DROP 再 CREATE
-- =============================================================

-- -------------------------------------------------------------
-- 暂存表 1：每个年级的最近一次考试
-- -------------------------------------------------------------
DROP TABLE IF EXISTS stg_latest_exam;
CREATE TABLE stg_latest_exam (
    grade      VARCHAR(20),            -- 年级
    exam_id    BIGINT       NOT NULL,  -- 考试ID
    exam_name  VARCHAR(100),           -- 考试名称
    exam_date  DATE                    -- 考试日期
);

-- -------------------------------------------------------------
-- 暂存表 2：关联各维度并计算年级内分科目名次的明细
-- -------------------------------------------------------------
DROP TABLE IF EXISTS stg_score_ranked;
CREATE TABLE stg_score_ranked (
    exam_id      BIGINT,
    exam_name    VARCHAR(100),
    exam_date    DATE,
    grade        VARCHAR(20),
    subject_id   BIGINT,
    subject_name VARCHAR(20),
    student_id   VARCHAR(20),
    student_name VARCHAR(50),
    gender       VARCHAR(4),
    class_id     BIGINT,
    class_name   VARCHAR(50),
    head_teacher VARCHAR(50),
    score        DECIMAL(5,1),
    subject_rank INT                    -- 年级内该科目名次（RANK，允许并列）
);

-- -------------------------------------------------------------
-- 目标报表表：年级各科前十名学生（含班主任）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS rpt_grade_subject_top10;
CREATE TABLE rpt_grade_subject_top10 (
    exam_id       BIGINT,
    exam_name     VARCHAR(100),
    exam_date     DATE,
    grade         VARCHAR(20),
    subject_id    BIGINT,
    subject_name  VARCHAR(20),
    subject_rank  INT,                  -- 年级内该科目名次（1=第一名）
    student_id    VARCHAR(20),
    student_name  VARCHAR(50),
    gender        VARCHAR(4),
    class_id      BIGINT,
    class_name    VARCHAR(50),
    head_teacher  VARCHAR(50),          -- 班主任姓名
    score         DECIMAL(5,1),
    etl_load_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
