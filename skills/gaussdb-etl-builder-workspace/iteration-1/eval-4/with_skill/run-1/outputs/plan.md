# ETL 执行计划

## 需求描述

基于考试域已沉淀的知识文档与源表，生成一张「各班级各科平均分」报表表：
按 **班级 × 科目** 粒度聚合成绩事实表的平均分。依据知识文档口径，统计平均分时剔除缺考记录（`is_absent = 0`）。

## 目标表

`rpt_class_subject_avg_score`

| 字段 | 类型 | 注释 |
|---|---|---|
| class_id | BIGINT NOT NULL | 班级ID |
| class_name | VARCHAR(50) | 班级名称 |
| grade | VARCHAR(20) | 年级 |
| subject_id | BIGINT NOT NULL | 科目ID |
| subject_name | VARCHAR(20) NOT NULL | 科目名称 |
| avg_score | DECIMAL(5,2) | 平均分（剔除缺考后 AVG(score)，保留2位小数） |
| score_cnt | INT NOT NULL | 参与统计的有效成绩条数（非缺考） |

## 数据来源与关联（复用知识文档关系）

- `fact_exam_score.student_id` ↔ `dim_student.student_id` （1:N）
- `dim_student.class_id` ↔ `dim_class.class_id` （N:1）
- `fact_exam_score.subject_id` ↔ `dim_subject.subject_id` （1:N）

以上 join key 与基数均直接取自知识文档，未再追问。

## ETL 步骤

### Step 1: 创建目标表

用确认的 schema 创建报表表 `rpt_class_subject_avg_score`。

输出表：`rpt_class_subject_avg_score`

### Step 2: 加载各班级各科平均分

`fact_exam_score` 关联 `dim_student → dim_class` 补班级、关联 `dim_subject` 补科目名；过滤 `is_absent = 0` 后，按班级 × 科目分组求 `AVG(score)`，一条 `INSERT ... SELECT` 写入目标表（无需临时表）。

输出表：`rpt_class_subject_avg_score`

## 备注

- 顺序铁律：目标表先建（Step 1）后写（Step 2）。
- 需求无日期/增量语义，且本次未提供平台变量文档，故按全量装载，未使用平台变量。
- 全部维度补齐使用 `INNER JOIN`（要求班级/科目均能匹配到成绩）。
