-- Step 1: 创建目标表 exam_highest_avg_class
CREATE TABLE IF NOT EXISTS exam_highest_avg_class (
    exam_name      VARCHAR(100)   NOT NULL  COMMENT '考试名称',
    exam_date      DATE           NOT NULL  COMMENT '考试时间',
    subject_name   VARCHAR(50)    NOT NULL  COMMENT '科目名称',
    class_name     VARCHAR(100)   NOT NULL  COMMENT '最高平均分班级',
    avg_score      DECIMAL(5,2)   NOT NULL  COMMENT '平均分'
);