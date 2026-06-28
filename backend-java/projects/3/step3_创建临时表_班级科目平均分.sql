-- Step 3: 创建临时表 tmp_class_avg，计算各科目各班级平均分
CREATE TEMPORARY TABLE tmp_class_avg AS
SELECT
    f.subject_id,
    f.class_id,
    ROUND(AVG(f.score), 2) AS avg_score
FROM fact_exam_score f
INNER JOIN tmp_latest_exam e ON f.exam_id = e.exam_id
WHERE f.is_absent = 0
GROUP BY f.subject_id, f.class_id;