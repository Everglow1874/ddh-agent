# GaussDB SQL 参考

GaussDB 使用 **PostgreSQL 兼容**语法。生成 ETL SQL 前读本文件,照这里的风格和约定写,能避开常见方言坑。

## 目录

- [类型映射与常见坑](#类型映射与常见坑)
- [建表模板](#建表模板)
- [临时表模板](#临时表模板)
- [常见转换套路](#常见转换套路)
- [完整 Worked Example:各科前十名](#完整-worked-example各科前十名)

## 类型映射与常见坑

| 用途 | 推荐类型 | 说明 |
|---|---|---|
| 短文本(姓名、编码) | `VARCHAR(n)` | 必须给长度;按业务上限留余量 |
| 长文本 | `TEXT` | 不定长 |
| 整数 | `INT` / `BIGINT` | 主键、计数用 `BIGINT` 更稳 |
| 金额/分数 | `DECIMAL(p,s)` / `NUMERIC(p,s)` | 不要用 `FLOAT` 存金额,会有精度问题 |
| 日期 | `DATE` | 仅日期 |
| 时间戳 | `TIMESTAMP` | 含时分秒 |
| 布尔/标志 | `INT`(0/1)或 `BOOLEAN` | 跟随源表既有约定 |

常见坑:
- `VARCHAR` 一定带长度,别裸写 `VARCHAR`。
- 日期函数用 PG 风格:`CURRENT_DATE`、`date_trunc('month', d)`、`d - INTERVAL '1 day'`、`to_char(d, 'YYYY-MM')`。不要用 MySQL 的 `DATE_FORMAT`、`DATE_SUB`。
- 字符串拼接用 `||` 或 `concat()`,不要用 `+`。
- 取 Top-N 用窗口函数 `ROW_NUMBER()`,不要依赖 `LIMIT` 来做"每组前 N"。

## 建表模板

目标表创建步骤(`is_temp_table=false`)用这个形态。字段后用 `--` 行内注释写中文口径,可读性好:

```sql
-- ============================================================
-- Step 1: 创建目标表 <target_table>
-- ============================================================
CREATE TABLE IF NOT EXISTS <target_table> (
    col_a    VARCHAR(100) NOT NULL,  -- 注释
    col_b    INT          NOT NULL,  -- 注释
    col_c    DECIMAL(5,1),           -- 注释
    col_d    VARCHAR(50)             -- 注释
);
```

可选:GaussDB(DWS)分布式形态支持 `DISTRIBUTE BY HASH(col)` 指定分布列。仅当用户明确要求或源表有明显分布键时才加,否则保持简单,省略让其走默认分布。

```sql
CREATE TABLE IF NOT EXISTS <target_table> (
    ...
) DISTRIBUTE BY HASH (id);
```

## 临时表模板

仅在确实需要中间结果落地时使用(`is_temp_table=true`)。能用 `WITH` CTE 在一条语句里算完的,就不要建临时表。

```sql
-- ============================================================
-- Step N: 创建临时表 <tmp_table>
-- ============================================================
CREATE TEMPORARY TABLE <tmp_table> (
    ...
);
```

记住顺序铁律:临时表、目标表都要在写入它们的步骤**之前**创建。

## 常见转换套路

**每组取前 N(窗口函数排名)**:
```sql
WITH ranked AS (
    SELECT
        t.*,
        ROW_NUMBER() OVER (PARTITION BY group_col ORDER BY sort_col DESC) AS rn
    FROM source_t t
)
SELECT * FROM ranked WHERE rn <= 10;
```

**去重(保留每键最新一条)**:
```sql
WITH dedup AS (
    SELECT
        t.*,
        ROW_NUMBER() OVER (PARTITION BY biz_key ORDER BY update_time DESC) AS rn
    FROM source_t t
)
SELECT * FROM dedup WHERE rn = 1;
```

**多表关联补字段(维度补齐)**:用 `INNER JOIN` 保证匹配、`LEFT JOIN` 允许缺失,按基数选择。1:N 关联注意是否会放大行数。

**按月聚合**:
```sql
SELECT
    user_id,
    to_char(order_date, 'YYYY-MM') AS stat_month,
    SUM(amount) AS total_amount,
    COUNT(*)    AS order_cnt
FROM fact_order
GROUP BY user_id, to_char(order_date, 'YYYY-MM');
```

## 完整 Worked Example:各科前十名

需求:统计最后一次考试,年级里各科前十名的学生信息以及班主任信息,生成一张报表表。

源表(示意):`dim_exam`(考试维度)、`fact_exam_score`(成绩事实,含 `is_absent`)、`dim_student`、`dim_class`(含 `head_teacher`)、`dim_subject`。

**目标表**:`rpt_exam_top10_by_subject`

**Step 1 — 创建目标表**(`is_temp_table=false`):
```sql
-- ============================================================
-- Step 1: 创建目标表 rpt_exam_top10_by_subject
-- ============================================================
CREATE TABLE IF NOT EXISTS rpt_exam_top10_by_subject (
    exam_name    VARCHAR(100) NOT NULL,  -- 考试名称
    subject_name VARCHAR(20)  NOT NULL,  -- 科目名称
    rank         INT          NOT NULL,  -- 排名（1-10）
    student_id   VARCHAR(20)  NOT NULL,  -- 学号
    student_name VARCHAR(50),            -- 学生姓名
    gender       VARCHAR(4),             -- 性别
    class_name   VARCHAR(50),            -- 班级名称
    grade        VARCHAR(20),            -- 年级
    score        DECIMAL(5,1),           -- 考试得分
    head_teacher VARCHAR(50)             -- 班主任姓名
);
```

**Step 2 — 加载各科前十名学生数据**(`is_temp_table=false`,用 CTE 一条语句算完,无需临时表):
```sql
-- ============================================================
-- Step 2: 加载各科前十名学生数据
-- ============================================================
INSERT INTO rpt_exam_top10_by_subject
WITH
-- 2.1 定位最后一次考试（按考试日期降序 + exam_id 降序取第一条）
latest_exam AS (
    SELECT exam_id, exam_name
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
-- 2.3 取排名 ≤ 10，关联维度补齐字段后写入目标表
SELECT
    le.exam_name,
    sub.subject_name,
    rs.rn          AS rank,
    stu.student_id,
    stu.student_name,
    stu.gender,
    cls.class_name,
    cls.grade,
    rs.score,
    cls.head_teacher
FROM ranked_scores rs
INNER JOIN latest_exam  le  ON rs.exam_id    = le.exam_id
INNER JOIN dim_student  stu ON rs.student_id = stu.student_id
INNER JOIN dim_class    cls ON stu.class_id  = cls.class_id
INNER JOIN dim_subject  sub ON rs.subject_id = sub.subject_id
WHERE rs.rn <= 10;
```

注意这个例子里:目标表先建(Step 1)后写(Step 2);排名用 `ROW_NUMBER() OVER (PARTITION BY ...)`;维度补齐用 `INNER JOIN`;整个加载一条 `INSERT ... SELECT` 完成,没有为了拆步骤而引入临时表。