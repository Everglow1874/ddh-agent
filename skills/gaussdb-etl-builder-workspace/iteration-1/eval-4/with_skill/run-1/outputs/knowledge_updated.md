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
- **字段**:
  - `subject_id` `BIGINT` —— 科目ID
  - `subject_name` `VARCHAR(20)` —— 科目名称

## 关联关系

- `fact_exam_score.student_id` ↔ `dim_student.student_id`  `1:N`  一个学生多条成绩
- `dim_student.class_id` ↔ `dim_class.class_id`  `N:1`  一个班级多个学生
- `fact_exam_score.subject_id` ↔ `dim_subject.subject_id`  `1:N`  一个科目多条成绩

## 字段口径 / 枚举

- `fact_exam_score.is_absent` 取值:`0=未缺考`,`1=缺考`。统计平均分时应剔除缺考记录(is_absent=0)。
- **各班级各科平均分**计算口径:剔除缺考(`is_absent=0`)后,按 `班级(class_id) × 科目(subject_id)` 分组对 `score` 求 `AVG`。
  关联路径:`fact_exam_score → dim_student(by student_id) → dim_class(by class_id)`,科目名取自 `dim_subject(by subject_id)`。
  产物报表表:`rpt_class_subject_avg_score`。
