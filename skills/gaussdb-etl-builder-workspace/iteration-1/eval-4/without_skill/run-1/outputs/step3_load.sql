-- =============================================================
-- Step 3: Load —— 聚合计算各班级各科平均分, 全量刷新写入目标表
-- 幂等: 先 TRUNCATE 再 INSERT
-- =============================================================

TRUNCATE TABLE rpt_class_subject_avg_score;

INSERT INTO rpt_class_subject_avg_score (
    class_id, class_name, grade,
    subject_id, subject_name,
    avg_score, student_cnt, score_cnt, etl_time
)
SELECT
    class_id,
    class_name,
    grade,
    subject_id,
    subject_name,
    ROUND(AVG(score), 2)            AS avg_score,
    COUNT(DISTINCT student_id)      AS student_cnt,
    COUNT(*)                        AS score_cnt,
    CURRENT_TIMESTAMP               AS etl_time
FROM tmp_exam_score_enriched
GROUP BY class_id, class_name, grade, subject_id, subject_name;
