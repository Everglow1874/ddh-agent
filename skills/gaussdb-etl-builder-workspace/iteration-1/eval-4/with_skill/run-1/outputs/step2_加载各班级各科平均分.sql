-- ============================================================
-- Step 2: 加载各班级各科平均分数据
-- 一条 INSERT ... SELECT 完成，无需临时表
-- 口径：剔除缺考记录（is_absent = 0），按 班级 × 科目 聚合求平均
-- ============================================================
INSERT INTO rpt_class_subject_avg_score
SELECT
    cls.class_id,
    cls.class_name,
    cls.grade,
    sub.subject_id,
    sub.subject_name,
    ROUND(AVG(f.score), 2) AS avg_score,   -- 平均分，保留2位小数
    COUNT(*)               AS score_cnt    -- 有效成绩条数
FROM fact_exam_score f
-- 学生 → 班级（知识文档：student_id 1:N、class_id N:1）
INNER JOIN dim_student stu ON f.student_id  = stu.student_id
INNER JOIN dim_class   cls ON stu.class_id  = cls.class_id
-- 科目维度补齐科目名称（知识文档：subject_id 1:N）
INNER JOIN dim_subject sub ON f.subject_id  = sub.subject_id
-- 统计平均分时剔除缺考记录（知识文档口径：is_absent=0 为未缺考）
WHERE f.is_absent = 0
GROUP BY
    cls.class_id,
    cls.class_name,
    cls.grade,
    sub.subject_id,
    sub.subject_name;
