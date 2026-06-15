-- =============================================================
-- 04_load_report.sql
-- 过滤名次 <= 10，全量装载目标报表表（幂等）
-- =============================================================

TRUNCATE TABLE rpt_grade_subject_top10;

INSERT INTO rpt_grade_subject_top10 (
    exam_id, exam_name, exam_date, grade,
    subject_id, subject_name, subject_rank,
    student_id, student_name, gender,
    class_id, class_name, head_teacher,
    score, etl_load_time
)
SELECT
    exam_id, exam_name, exam_date, grade,
    subject_id, subject_name, subject_rank,
    student_id, student_name, gender,
    class_id, class_name, head_teacher,
    score,
    CURRENT_TIMESTAMP
FROM stg_score_ranked
WHERE subject_rank <= 10
ORDER BY grade, subject_id, subject_rank, score DESC, student_id;
