-- ============================================================
-- Step 1: 创建目标表 rpt_exam_top10_by_subject
-- 报表表：最近一次考试，年级里各科前十名的学生信息及班主任信息
-- ============================================================
CREATE TABLE IF NOT EXISTS rpt_exam_top10_by_subject (
    exam_id      BIGINT       NOT NULL,  -- 考试ID
    exam_name    VARCHAR(100) NOT NULL,  -- 考试名称
    exam_date    DATE         NOT NULL,  -- 考试日期
    grade        VARCHAR(20),            -- 年级
    subject_id   BIGINT       NOT NULL,  -- 科目ID
    subject_name VARCHAR(20)  NOT NULL,  -- 科目名称
    rank         INT          NOT NULL,  -- 科目内排名（1-10）
    student_id   VARCHAR(20)  NOT NULL,  -- 学号
    student_name VARCHAR(50),            -- 学生姓名
    gender       VARCHAR(4),             -- 性别
    class_id     BIGINT,                 -- 班级ID
    class_name   VARCHAR(50),            -- 班级名称
    score        DECIMAL(5,1),           -- 考试得分
    head_teacher VARCHAR(50)             -- 班主任姓名
);
