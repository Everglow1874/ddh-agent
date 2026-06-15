-- 考试域源表结构(GaussDB / PG 兼容)

-- 考试维度
CREATE TABLE dim_exam (
    exam_id    BIGINT       NOT NULL,  -- 考试ID
    exam_name  VARCHAR(100) NOT NULL,  -- 考试名称
    exam_date  DATE         NOT NULL,  -- 考试日期
    grade      VARCHAR(20)             -- 年级
);

-- 成绩事实表
CREATE TABLE fact_exam_score (
    exam_id    BIGINT       NOT NULL,  -- 考试ID
    subject_id BIGINT       NOT NULL,  -- 科目ID
    student_id VARCHAR(20)  NOT NULL,  -- 学号
    score      DECIMAL(5,1),           -- 得分
    is_absent  INT          NOT NULL   -- 是否缺考 0=否 1=是
);

-- 学生维度
CREATE TABLE dim_student (
    student_id   VARCHAR(20) NOT NULL,  -- 学号
    student_name VARCHAR(50),           -- 姓名
    gender       VARCHAR(4),            -- 性别
    class_id     BIGINT                 -- 班级ID
);

-- 班级维度
CREATE TABLE dim_class (
    class_id     BIGINT      NOT NULL,  -- 班级ID
    class_name   VARCHAR(50),           -- 班级名称
    grade        VARCHAR(20),           -- 年级
    head_teacher VARCHAR(50)            -- 班主任姓名
);

-- 科目维度
CREATE TABLE dim_subject (
    subject_id   BIGINT      NOT NULL,  -- 科目ID
    subject_name VARCHAR(20) NOT NULL   -- 科目名称
);
