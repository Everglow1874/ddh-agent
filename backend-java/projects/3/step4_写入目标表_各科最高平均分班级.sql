-- Step 4: 写入目标表 exam_highest_avg_class，各科最高平均分班级
INSERT INTO exam_highest_avg_class (exam_name, exam_date, subject_name, class_name, avg_score)
SELECT
    e.exam_name,
    e.exam_date,
    s.subject_name,
    c.class_name,
    r.avg_score
FROM (
    SELECT
        subject_id,
        class_id,
        avg_score,
        RANK() OVER (PARTITION BY subject_id ORDER BY avg_score DESC) AS rn
    FROM tmp_class_avg
) r
INNER JOIN dim_subject s ON r.subject_id = s.subject_id
INNER JOIN dim_class   c ON r.class_id   = c.class_id
CROSS JOIN tmp_latest_exam e
WHERE r.rn = 1
ORDER BY s.subject_name;