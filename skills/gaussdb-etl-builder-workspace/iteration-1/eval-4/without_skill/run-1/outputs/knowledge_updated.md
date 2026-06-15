# 数据域知识文档:考试域

> 本文档沉淀已确认的可复用知识,跨会话累积。

## 表清单

### 表:fact_exam_score

- **用途**:成绩事实表,记录每个学生每场考试每个科目的得分
- **字段**:
  - `exam_id` `BIGINT` —— 考试ID
  - `subject_id` `BIGINT` —— 科目ID
  - `student_id` `VARCHAR(20)` —— 学号
  - `score` `DECIMAL(5,1)` —— 得分
  - `is_absent` `INT` —— 是否缺考

### 表:dim_student

- **用途**:学生维度
- **字段**:`student_id`、`student_name`、`gender`、`class_id`

### 表:dim_class

- **用途**:班级维度,含班主任
- **字段**:`class_id`、`class_name`、`grade`、`head_teacher`

### 表:dim_subject

- **用途**:科目维度
- **字段**(本次补全):
  - `subject_id` `BIGINT` —— 科目ID
  - `subject_name` `VARCHAR(20)` —— 科目名称

### 表:dim_exam(本次新增)

- **用途**:考试维度
- **字段**:
  - `exam_id` `BIGINT` —— 考试ID
  - `exam_name` `VARCHAR(100)` —— 考试名称
  - `exam_date` `DATE` —— 考试日期
  - `grade` `VARCHAR(20)` —— 年级

### 表:rpt_class_subject_avg_score(本次新增,报表表)

- **用途**:各班级各科平均分报表,按 `(class_id, subject_id)` 粒度沉淀平均分
- **字段**:
  - `class_id` `BIGINT` —— 班级ID
  - `class_name` `VARCHAR(50)` —— 班级名称
  - `grade` `VARCHAR(20)` —— 年级
  - `subject_id` `BIGINT` —— 科目ID
  - `subject_name` `VARCHAR(20)` —— 科目名称
  - `avg_score` `DECIMAL(6,2)` —— 平均分(剔除缺考后)
  - `student_cnt` `INT` —— 参与统计学生数(去重)
  - `score_cnt` `INT` —— 参与统计成绩条数
  - `etl_time` `TIMESTAMP` —— ETL执行时间
- **生产方式**:全量刷新(TRUNCATE + INSERT),数据源见下文口径

## 关联关系

- `fact_exam_score.student_id` ↔ `dim_student.student_id`  `1:N`  一个学生多条成绩
- `dim_student.class_id` ↔ `dim_class.class_id`  `N:1`  一个班级多个学生
- `fact_exam_score.subject_id` ↔ `dim_subject.subject_id`  `1:N`  一个科目多条成绩
- `fact_exam_score.exam_id` ↔ `dim_exam.exam_id`  `1:N`  一场考试多条成绩(本次补充)

## 字段口径 / 枚举

- `fact_exam_score.is_absent` 取值:`0=未缺考`,`1=缺考`。统计平均分时应剔除缺考记录(is_absent=0)。
- **各班级各科平均分口径**(本次沉淀):
  - 班级来源:经 `fact_exam_score → dim_student → dim_class` 关联得到 `class_id` / `class_name`。
  - 过滤条件:`is_absent = 0` 且 `score IS NOT NULL`(剔除缺考与脏空分)。
  - 聚合粒度:`(class_id, subject_id)`,度量为 `AVG(score)`,保留 2 位小数。
