# ETL 执行计划

## 需求描述

统计最近一次考试，年级里各科前十名的学生信息以及班主任信息，生成一张报表表。

## 目标表

`rpt_exam_top10_by_subject`

| 字段 | 类型 | 注释 |
|---|---|---|
| exam_id | BIGINT | 考试ID |
| exam_name | VARCHAR(100) | 考试名称 |
| exam_date | DATE | 考试日期 |
| grade | VARCHAR(20) | 年级 |
| subject_id | BIGINT | 科目ID |
| subject_name | VARCHAR(20) | 科目名称 |
| rank | INT | 科目内排名（1-10） |
| student_id | VARCHAR(20) | 学号 |
| student_name | VARCHAR(50) | 学生姓名 |
| gender | VARCHAR(4) | 性别 |
| class_id | BIGINT | 班级ID |
| class_name | VARCHAR(50) | 班级名称 |
| score | DECIMAL(5,1) | 考试得分 |
| head_teacher | VARCHAR(50) | 班主任姓名 |

## ETL 步骤

### Step 1: 创建目标表

用确认的 schema 创建报表表 `rpt_exam_top10_by_subject`。先建后写。

输出表：`rpt_exam_top10_by_subject`

### Step 2: 加载各科前十名学生数据

一条 `INSERT ... SELECT` 配合 `WITH` CTE 完成，无需临时表：
1. `latest_exam`：从 `dim_exam` 按 `exam_date DESC, exam_id DESC` 取最近一次考试；
2. `ranked_scores`：关联该次考试、过滤缺考（`is_absent = 0`），按 `subject_id` 分区、`score` 降序用 `ROW_NUMBER()` 排名；
3. 取 `rn <= 10`，关联 `dim_student`/`dim_subject`（INNER JOIN）与 `dim_class`（LEFT JOIN，允许班级/班主任缺失）补齐字段，写入目标表。

输出表：`rpt_exam_top10_by_subject`

## 设计说明

- 关联键：`fact_exam_score.exam_id = dim_exam.exam_id`、`fact_exam_score.student_id = dim_student.student_id`、`fact_exam_score.subject_id = dim_subject.subject_id`、`dim_student.class_id = dim_class.class_id`。
- 取「每科前十」用窗口函数 `ROW_NUMBER() OVER (PARTITION BY subject_id ORDER BY score DESC)`，不用 `LIMIT`。
- 缺考成绩（`is_absent = 1`）排除在排名之外。
- 未提供平台变量文档，「最近一次考试」用 `dim_exam` 日期排序动态取得，不写死日期。
