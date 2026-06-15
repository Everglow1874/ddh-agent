-- =============================================================
-- 02_stg_latest_exam.sql
-- 计算每个年级的“最近一次考试”
-- 口径：按 grade 分组，取 exam_date 最新；同日取 exam_id 最大者
-- =============================================================

TRUNCATE TABLE stg_latest_exam;

INSERT INTO stg_latest_exam (grade, exam_id, exam_name, exam_date)
SELECT grade, exam_id, exam_name, exam_date
FROM (
    SELECT
        grade,
        exam_id,
        exam_name,
        exam_date,
        ROW_NUMBER() OVER (
            PARTITION BY grade
            ORDER BY exam_date DESC, exam_id DESC
        ) AS rn
    FROM dim_exam
    WHERE grade IS NOT NULL
) t
WHERE rn = 1;

-- 如只需全库唯一一场最新考试，可改为：
--   ... ROW_NUMBER() OVER (ORDER BY exam_date DESC, exam_id DESC) AS rn ...
--   并去掉 WHERE grade IS NOT NULL 中的分组语义。
