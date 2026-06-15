-- =============================================================
-- Step 1: DDL —— 建目标报表表 + 临时表
-- 目标: 各班级各科平均分报表
-- 引擎: GaussDB (PostgreSQL 兼容)
-- =============================================================

-- 1.1 目标报表表(若不存在则创建)
CREATE TABLE IF NOT EXISTS rpt_class_subject_avg_score (
    class_id      BIGINT       NOT NULL,                  -- 班级ID
    class_name    VARCHAR(50),                            -- 班级名称
    grade         VARCHAR(20),                            -- 年级
    subject_id    BIGINT       NOT NULL,                  -- 科目ID
    subject_name  VARCHAR(20),                            -- 科目名称
    avg_score     DECIMAL(6,2),                           -- 平均分(剔除缺考后)
    student_cnt   INT,                                    -- 参与统计学生数(去重)
    score_cnt     INT,                                    -- 参与统计成绩条数
    etl_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP, -- ETL执行时间
    CONSTRAINT pk_rpt_class_subject_avg_score PRIMARY KEY (class_id, subject_id)
);

COMMENT ON TABLE  rpt_class_subject_avg_score IS '各班级各科平均分报表(剔除缺考)';
COMMENT ON COLUMN rpt_class_subject_avg_score.avg_score IS '平均分,过滤 is_absent=0 且 score 非空后计算';

-- 1.2 临时表(会话级,存放关联+过滤后的成绩明细)
DROP TABLE IF EXISTS tmp_exam_score_enriched;
CREATE TEMPORARY TABLE tmp_exam_score_enriched (
    class_id      BIGINT,
    class_name    VARCHAR(50),
    grade         VARCHAR(20),
    subject_id    BIGINT,
    subject_name  VARCHAR(20),
    student_id    VARCHAR(20),
    score         DECIMAL(5,1)
);
