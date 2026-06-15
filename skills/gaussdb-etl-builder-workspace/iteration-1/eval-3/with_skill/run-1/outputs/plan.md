# ETL 执行计划

## 需求描述

每天 T+1 跑的增量作业:把交易流水明细表(`ods_trade_detail`)按天增量汇总成一张每日交易汇总表,
按「交易日期 + 渠道」统计交易金额(SUM)和交易笔数(COUNT)。

## 源表

`ods_trade_detail` —— 交易流水明细表(按 `tx_date` 分区,每天大量记录)

| 字段 | 类型 | 含义 |
|---|---|---|
| `tx_id` | BIGINT | 流水ID |
| `tx_date` | DATE | 交易日期 |
| `user_id` | BIGINT | 用户ID |
| `channel` | VARCHAR(20) | 交易渠道(可空) |
| `amount` | DECIMAL(12,2) | 交易金额 |
| `tx_type` | VARCHAR(20) | 交易类型 PAY/REFUND |

## 目标表

`rpt_trade_daily_summary` —— 每日交易汇总(报表表,`rpt_` 前缀)

| 字段 | GaussDB 类型 | 注释 |
|---|---|---|
| `stat_date` | DATE NOT NULL | 统计日期(交易日,= 源表 `tx_date`) |
| `channel` | VARCHAR(20) NOT NULL | 交易渠道,源表空值归一为 `'UNKNOWN'` |
| `total_amount` | DECIMAL(18,2) NOT NULL | 当日该渠道交易总金额 `SUM(amount)` |
| `tx_cnt` | BIGINT NOT NULL | 当日该渠道交易笔数 `COUNT(*)` |
| `etl_date` | DATE | 加工日期(作业执行当天,`'${RUN_DATE}'`) |

> 口径说明:本汇总不区分 `tx_type`,对当日同一渠道的所有流水统一求和与计数。
> 若后续需要拆分 PAY / REFUND,可把 `tx_type` 加入分组维度。

## 平台变量

| 变量 | 含义 | 用途 |
|---|---|---|
| `${TX_DATE}` | 作业实例执行日的前一天(`YYYY-MM-DD`) | T+1 增量过滤 / 幂等删除的日期键 |
| `${RUN_DATE}` | 执行当天日期(`YYYY-MM-DD`) | 记录加工日期 `etl_date` |

## ETL 步骤

### Step 1: 创建目标表

用确认的 schema 创建 `rpt_trade_daily_summary`(若不存在)。

输出表:`rpt_trade_daily_summary`

### Step 2: 清理当日增量数据

T+1 增量幂等处理:写入前先删除目标表中 `stat_date = '${TX_DATE}'` 的旧汇总,
保证作业重跑不会重复累加。

输出表:`rpt_trade_daily_summary`

### Step 3: 加载每日交易汇总

从 `ods_trade_detail` 取 `tx_date = '${TX_DATE}'` 当天的流水,
按 `tx_date + channel`(空渠道归一为 `'UNKNOWN'`)聚合 `SUM(amount)` 与 `COUNT(*)`,写入目标表。

输出表:`rpt_trade_daily_summary`

> 顺序铁律已满足:目标表在 Step 1 创建,Step 2/3 才对其写入。无需临时表——一条
> `INSERT ... SELECT ... GROUP BY` 即可完成聚合装载。
