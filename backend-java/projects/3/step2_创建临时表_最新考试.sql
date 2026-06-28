-- Step 2: 创建临时表 tmp_latest_exam，存放最新一场考试
CREATE TEMPORARY TABLE tmp_latest_exam AS
SELECT exam_id, exam_name, exam_date
FROM dim_exam
ORDER BY exam_date DESC
LIMIT 1;