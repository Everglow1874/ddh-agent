-- ============================================================
-- Step 1: 创建目标表 rpt_class_subject_avg_score
-- 各班级各科平均分报表表(班级 × 科目 粒度）
-- ============================================================
CREATE TABLE IF NOT EXISTS rpt_class_subject_avg_score (
    class_id     BIGINT       NOT NULL,  -- 班级ID
    class_name   VARCHAR(50),            -- 班级名称
    grade        VARCHAR(20),            -- 年级
    subject_id   BIGINT       NOT NULL,  -- 科目ID
    subject_name VARCHAR(20)  NOT NULL,  -- 科目名称
    avg_score    DECIMAL(5,2),           -- 平均分（剔除缺考后 AVG(score)，保留2位小数）
    score_cnt    INT          NOT NULL   -- 参与统计的有效成绩条数（非缺考）
);
