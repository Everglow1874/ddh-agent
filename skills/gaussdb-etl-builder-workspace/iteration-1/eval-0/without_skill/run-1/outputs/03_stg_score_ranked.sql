-- =============================================================
-- 03_stg_score_ranked.sql
-- 关联成绩 + 学生 + 班级 + 科目，按(年级,科目)计算名次
-- 过滤缺考与空分；RANK() 允许同分并列
-- =============================================================

TRUNCATE TABLE stg_score_ranked;

INSERT INTO stg_score_ranked (
    exam_id, exam_name, exam_date, grade,
    subject_id, subject_name,
    student_id, student_name, gender,
    class_id, class_name, head_teacher,
    score, subject_rank
)
SELECT
    le.exam_id,
    le.exam_name,
    le.exam_date,
    le.grade,
    sub.subject_id,
    sub.subject_name,
    s.student_id,
    s.student_name,
    s.gender,
    c.class_id,
    c.class_name,
    c.head_teacher,
    f.score,
    RANK() OVER (
        PARTITION BY le.grade, sub.subject_id
        ORDER BY f.score DESC
    ) AS subject_rank
FROM stg_latest_exam      le
JOIN fact_exam_score      f   ON f.exam_id    = le.exam_id
JOIN dim_subject          sub ON sub.subject_id = f.subject_id
JOIN dim_student          s   ON s.student_id  = f.student_id
LEFT JOIN dim_class       c   ON c.class_id    = s.class_id
WHERE f.is_absent = 0
  AND f.score IS NOT NULL;
