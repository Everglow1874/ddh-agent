# ETL 作业计划:各班级各科平均分报表

## 1. 目标

构建报表表 `rpt_class_subject_avg_score`(各班级各科平均分),供班级/科目维度的成绩分析使用。

## 2. 数据来源与口径

| 来源表 | 用途 | 关键字段 |
| --- | --- | --- |
| `fact_exam_score` | 成绩事实(度量来源) | `student_id`、`subject_id`、`score`、`is_absent` |
| `dim_student` | 取学生所属班级 | `student_id`、`class_id` |
| `dim_class` | 取班级名称、年级 | `class_id`、`class_name`、`grade` |
| `dim_subject` | 取科目名称 | `subject_id`、`subject_name` |

**关联关系**(依据知识文档):
- `fact_exam_score.student_id = dim_student.student_id` (N:1)
- `dim_student.class_id = dim_class.class_id` (N:1)
- `fact_exam_score.subject_id = dim_subject.subject_id` (N:1)

**核心口径**:统计平均分时必须剔除缺考记录,即过滤 `is_absent = 0`(知识文档「字段口径」明确规定)。同时剔除 `score IS NULL` 的记录,避免脏数据影响均值。

## 3. 目标表结构 `rpt_class_subject_avg_score`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `class_id` | BIGINT | 班级ID |
| `class_name` | VARCHAR(50) | 班级名称 |
| `grade` | VARCHAR(20) | 年级 |
| `subject_id` | BIGINT | 科目ID |
| `subject_name` | VARCHAR(20) | 科目名称 |
| `avg_score` | DECIMAL(6,2) | 平均分(剔除缺考后) |
| `student_cnt` | INT | 参与统计的学生数(去重) |
| `score_cnt` | INT | 参与统计的成绩条数 |
| `etl_time` | TIMESTAMP | ETL 执行时间 |

主键逻辑:`(class_id, subject_id)`。

## 4. 执行步骤(粗粒度)

1. **step1_ddl.sql** —— 建目标表 `rpt_class_subject_avg_score` 与临时表 `tmp_exam_score_enriched`(若已存在则跳过/重建临时表)。
2. **step2_transform.sql** —— 关联事实表与各维度表,按口径过滤(`is_absent=0` 且 `score IS NOT NULL`),将明细落到临时表 `tmp_exam_score_enriched`。
3. **step3_load.sql** —— 基于临时表按 `(class_id, subject_id)` 聚合计算平均分,全量刷新写入目标表(先 TRUNCATE 再 INSERT)。
4. **step4_validate.sql** —— 数据质量校验(行数、是否有空均值、缺考是否被正确剔除)。

## 5. 调度与幂等

- 全量刷新模式:每次运行先 `TRUNCATE` 目标表再写入,天然幂等,可重复执行。
- 临时表使用 `TEMPORARY` 会话级表,事务/会话结束自动回收。
- 自动化非交互执行,按需默认确认。

## 6. 知识文档更新

执行完成后按知识文档「跨会话累积」规则更新,产出 `knowledge_updated.md`:
- 补充缺失的 `dim_exam` 表说明。
- 补全 `dim_subject` 的字段清单(`subject_id`、`subject_name`)。
- 登记新产出的报表表 `rpt_class_subject_avg_score`。
- 沉淀「各班级各科平均分」口径(剔除缺考 + 剔除空分)。
