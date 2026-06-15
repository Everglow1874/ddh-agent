-- =============================================================
-- Step 4: Validate —— 数据质量校验
-- 自动化运行时可将以下查询结果纳入日志/断言
-- =============================================================

-- 4.1 目标表总行数(应 > 0)
SELECT 'row_count' AS check_item, COUNT(*) AS val
FROM rpt_class_subject_avg_score;

-- 4.2 是否存在空均值(应为 0)
SELECT 'null_avg_score' AS check_item, COUNT(*) AS val
FROM rpt_class_subject_avg_score
WHERE avg_score IS NULL;

-- 4.3 平均分是否落在合法区间 [0,150](按典型满分, 越界即异常)
SELECT 'avg_score_out_of_range' AS check_item, COUNT(*) AS val
FROM rpt_class_subject_avg_score
WHERE avg_score < 0 OR avg_score > 150;

-- 4.4 缺考剔除核对: 目标表 score_cnt 总和应等于源表中 is_absent=0 且 score 非空的记录数
SELECT 'absent_excluded_match' AS check_item,
       CASE WHEN
            (SELECT COALESCE(SUM(score_cnt), 0) FROM rpt_class_subject_avg_score)
          = (SELECT COUNT(*) FROM fact_exam_score WHERE is_absent = 0 AND score IS NOT NULL)
       THEN 1 ELSE 0 END AS val;  -- 1=通过

-- 4.5 主键唯一性核对(应为 0 重复组)
SELECT 'dup_pk' AS check_item, COUNT(*) AS val
FROM (
    SELECT class_id, subject_id
    FROM rpt_class_subject_avg_score
    GROUP BY class_id, subject_id
    HAVING COUNT(*) > 1
) t;
