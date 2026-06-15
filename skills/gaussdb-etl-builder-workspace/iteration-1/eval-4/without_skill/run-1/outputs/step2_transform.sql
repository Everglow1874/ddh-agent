-- =============================================================
-- Step 2: Transform —— 关联事实表与维度表, 按口径过滤后落临时表
-- 口径: 剔除缺考 (is_absent = 0), 剔除空分 (score IS NOT NULL)
-- =============================================================

TRUNCATE TABLE tmp_exam_score_enriched;

INSERT INTO tmp_exam_score_enriched (
    class_id, class_name, grade,
    subject_id, subject_name,
    student_id, score
)
SELECT
    dc.class_id,
    dc.class_name,
    dc.grade,
    ds.subject_id,
    ds.subject_name,
    f.student_id,
    f.score
FROM fact_exam_score f
JOIN dim_student  stu ON f.student_id = stu.student_id
JOIN dim_class    dc  ON stu.class_id = dc.class_id
JOIN dim_subject  ds  ON f.subject_id = ds.subject_id
WHERE f.is_absent = 0          -- 剔除缺考(知识文档口径)
  AND f.score IS NOT NULL;     -- 剔除空分, 避免影响均值
