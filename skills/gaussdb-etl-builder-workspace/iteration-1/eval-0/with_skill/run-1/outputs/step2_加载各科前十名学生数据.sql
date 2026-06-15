-- ============================================================
-- Step 2: 加载各科前十名学生数据
-- 一条 INSERT ... SELECT 配合 CTE 算完，无需临时表
-- ============================================================
INSERT INTO rpt_exam_top10_by_subject (
    exam_id, exam_name, exam_date, grade,
    subject_id, subject_name, rank,
    student_id, student_name, gender,
    class_id, class_name, score, head_teacher
)
WITH
-- 2.1 定位最近一次考试（按考试日期降序 + exam_id 降序取第一条）
latest_exam AS (
    SELECT exam_id, exam_name, exam_date, grade
    FROM dim_exam
    ORDER BY exam_date DESC, exam_id DESC
    LIMIT 1
),
-- 2.2 仅取该次考试且非缺考的成绩，按科目分区、得分降序排名
ranked_scores AS (
    SELECT
        f.exam_id,
        f.subject_id,
        f.student_id,
        f.score,
        ROW_NUMBER() OVER (PARTITION BY f.subject_id ORDER BY f.score DESC) AS rn
    FROM fact_exam_score f
    INNER JOIN latest_exam le ON f.exam_id = le.exam_id
    WHERE f.is_absent = 0
)
-- 2.3 取科目内排名 <= 10，关联维度补齐学生、班级、班主任、科目信息后写入目标表
SELECT
    le.exam_id,
    le.exam_name,
    le.exam_date,
    le.grade,
    sub.subject_id,
    sub.subject_name,
    rs.rn            AS rank,
    stu.student_id,
    stu.student_name,
    stu.gender,
    cls.class_id,
    cls.class_name,
    rs.score,
    cls.head_teacher
FROM ranked_scores rs
INNER JOIN latest_exam le  ON rs.exam_id    = le.exam_id
INNER JOIN dim_student stu ON rs.student_id = stu.student_id
INNER JOIN dim_subject sub ON rs.subject_id = sub.subject_id
LEFT  JOIN dim_class   cls ON stu.class_id  = cls.class_id
WHERE rs.rn <= 10;
