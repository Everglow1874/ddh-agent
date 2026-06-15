# ETL 作业计划：年级各科前十名学生报表（含班主任）

## 1. 业务需求

统计**最近一次考试**中，**年级里各科前十名**的学生信息以及对应**班主任信息**，
落地为一张可直接查询的报表表 `rpt_grade_subject_top10`。

目标平台：GaussDB（PostgreSQL 兼容）数据仓库。

## 2. 源表（来自 inputs/exam_source_tables.sql）

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `dim_exam` | 考试维度 | exam_id, exam_name, exam_date, grade |
| `fact_exam_score` | 成绩事实表 | exam_id, subject_id, student_id, score, is_absent |
| `dim_student` | 学生维度 | student_id, student_name, gender, class_id |
| `dim_class` | 班级维度 | class_id, class_name, grade, head_teacher |
| `dim_subject` | 科目维度 | subject_id, subject_name |

## 3. 口径与设计决策

1. **“最近一次考试”的定义**：`dim_exam` 中每条考试记录都带 `grade`。
   按年级分组，取该年级 `exam_date` 最新的一场考试；
   若同一年级同一天有多场考试，用 `exam_id` 最大者兜底。
   这样报表会覆盖每个年级各自的“最近一次考试”，而不仅是全库唯一一场。
   （自动化运行，默认采用此口径；若只需全库唯一一场最新考试，可在 02 步去掉
   `PARTITION BY grade`。）

2. **年级归属**：成绩通过 `exam_id` 关联到考试，考试自带 `grade`，
   因此排名严格按“该年级该场考试”的成绩，避免跨年级混排。

3. **参与排名的成绩过滤**：剔除缺考（`is_absent = 1`）与空分数（`score IS NULL`），
   这些记录不参与名次计算。

4. **排名函数**：使用 `RANK()`（`PARTITION BY 年级, 科目 ORDER BY score DESC`）。
   同分并列同名次，保留名次 `<= 10`。因此并列情况下同一科目可能返回多于 10 行
   （例如第 10 名有 3 人并列，则 10 名位置出现 3 行），符合“前十名”的公平口径。
   如需严格 10 行可改用 `ROW_NUMBER()`。

5. **班主任信息**：通过 `dim_student.class_id -> dim_class.class_id` 取
   `class_name` 与 `head_teacher`。用 `LEFT JOIN` 保证学生无班级映射时仍保留成绩行
   （班级/班主任为空）。

6. **加载方式**：全量重跑（幂等）。每步先 `TRUNCATE` 暂存/目标表再装载，
   可重复执行不产生重复数据。

## 4. 数据流（分层）

```
源表(dim_exam/fact_exam_score/dim_student/dim_class/dim_subject)
   │
   ├─[02] 计算每个年级最近一次考试  ──►  stg_latest_exam
   │
   ├─[03] 关联成绩+学生+班级+科目并按(年级,科目)打分名次 ──► stg_score_ranked
   │
   └─[04] 过滤 rank<=10 装载报表  ──►  rpt_grade_subject_top10
```

## 5. 执行步骤与文件

| 顺序 | 文件 | 作用 |
|------|------|------|
| 1 | `01_ddl.sql` | 创建暂存表与目标报表表 |
| 2 | `02_stg_latest_exam.sql` | 计算每个年级“最近一次考试” |
| 3 | `03_stg_score_ranked.sql` | 关联各维度并计算年级内分科目名次 |
| 4 | `04_load_report.sql` | 过滤前十名，全量装载报表表 |
| 5 | `05_validation.sql` | 数据质量校验查询 |

执行顺序：01 → 02 → 03 → 04 → 05。

## 6. 目标报表表 `rpt_grade_subject_top10` 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| exam_id | BIGINT | 考试ID |
| exam_name | VARCHAR(100) | 考试名称 |
| exam_date | DATE | 考试日期 |
| grade | VARCHAR(20) | 年级 |
| subject_id | BIGINT | 科目ID |
| subject_name | VARCHAR(20) | 科目名称 |
| subject_rank | INT | 年级内该科目名次（1=第一名） |
| student_id | VARCHAR(20) | 学号 |
| student_name | VARCHAR(50) | 学生姓名 |
| gender | VARCHAR(4) | 性别 |
| class_id | BIGINT | 班级ID |
| class_name | VARCHAR(50) | 班级名称 |
| head_teacher | VARCHAR(50) | 班主任姓名 |
| score | DECIMAL(5,1) | 该科目得分 |
| etl_load_time | TIMESTAMP | ETL 装载时间 |

## 7. 校验要点（详见 05_validation.sql）

- 报表非空、各年级各科目均有数据；
- 关键字段（学号、姓名、科目、名次）无 NULL；
- 名次从 1 连续递增（允许并列）；
- 报表中分数与源事实表一致；
- 不含缺考/空分记录。
