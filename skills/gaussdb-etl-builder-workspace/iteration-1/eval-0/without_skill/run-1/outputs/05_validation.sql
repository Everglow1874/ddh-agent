-- =============================================================
-- 05_validation.sql
-- 数据质量校验（执行后人工/自动检查结果是否符合预期）
-- =============================================================

-- 1) 报表总行数（应 > 0）
SELECT COUNT(*) AS total_rows FROM rpt_grade_subject_top10;

-- 2) 覆盖的年级 / 科目数，以及每个(年级,科目)的行数与最大名次
--    正常情况下 max_rank <= 10；若 row_cnt > 10 说明第10名存在并列（符合 RANK 口径）
SELECT
    grade,
    subject_id,
    subject_name,
    COUNT(*)          AS row_cnt,
    MIN(subject_rank) AS min_rank,
    MAX(subject_rank) AS max_rank
FROM rpt_grade_subject_top10
GROUP BY grade, subject_id, subject_name
ORDER BY grade, subject_id;

-- 3) 关键字段空值检查（应全部为 0）
SELECT
    SUM(CASE WHEN student_id   IS NULL THEN 1 ELSE 0 END) AS null_student_id,
    SUM(CASE WHEN student_name IS NULL THEN 1 ELSE 0 END) AS null_student_name,
    SUM(CASE WHEN subject_id   IS NULL THEN 1 ELSE 0 END) AS null_subject_id,
    SUM(CASE WHEN subject_rank IS NULL THEN 1 ELSE 0 END) AS null_rank,
    SUM(CASE WHEN score        IS NULL THEN 1 ELSE 0 END) AS null_score
FROM rpt_grade_subject_top10;

-- 4) 班主任缺失检查（理论上应为 0；非 0 说明存在学生未映射到班级）
SELECT COUNT(*) AS missing_head_teacher
FROM rpt_grade_subject_top10
WHERE head_teacher IS NULL;

-- 5) 名次起点检查：每个(年级,科目)名次应从 1 开始
SELECT grade, subject_id, subject_name, MIN(subject_rank) AS first_rank
FROM rpt_grade_subject_top10
GROUP BY grade, subject_id, subject_name
HAVING MIN(subject_rank) <> 1;
--  正常应返回 0 行

-- 6) 装载的考试与“最近一次考试”一致性检查（应返回 0 行）
SELECT r.grade, r.exam_id
FROM rpt_grade_subject_top10 r
LEFT JOIN stg_latest_exam le
       ON le.grade = r.grade AND le.exam_id = r.exam_id
WHERE le.exam_id IS NULL
GROUP BY r.grade, r.exam_id;

-- 7) 预览：各年级各科目前十名
SELECT
    grade, exam_name, exam_date,
    subject_name, subject_rank,
    student_id, student_name, gender,
    class_name, head_teacher, score
FROM rpt_grade_subject_top10
ORDER BY grade, subject_id, subject_rank, score DESC, student_id;
