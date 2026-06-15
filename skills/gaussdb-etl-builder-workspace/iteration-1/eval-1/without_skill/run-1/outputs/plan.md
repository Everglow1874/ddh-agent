# ETL 作业计划：用户月度消费宽表

## 1. 目标

将交易域订单表 `ods_orders` 与用户维表 `ods_users` 加工成一张
**按自然月聚合的用户消费宽表** `dws_user_consume_monthly`。

宽表粒度：**一个用户 × 一个自然月 = 一行**。

核心指标：

| 字段 | 含义 | 计算口径 |
|------|------|----------|
| `consume_amount` | 消费总金额 | 当月该用户**有效消费**金额合计 |
| `order_cnt` | 订单数 | 当月该用户**有效订单**笔数 |
| `city` | 所在城市 | 来自用户维表 `ods_users.city` |

## 2. 源表结构

### ods_orders（订单表）
| 列 | 类型 | 说明 |
|----|------|------|
| order_id | BIGINT NOT NULL | 订单 ID |
| user_id | BIGINT NOT NULL | 用户 ID |
| order_date | DATE NOT NULL | 下单日期 |
| amount | DECIMAL(12,2) NOT NULL | 订单金额 |
| status | VARCHAR(20) | 订单状态 PAID / REFUND / CANCEL |

### ods_users（用户表）
| 列 | 类型 | 说明 |
|----|------|------|
| user_id | BIGINT NOT NULL | 用户 ID |
| user_name | VARCHAR(50) | 用户名 |
| city | VARCHAR(50) | 城市 |
| reg_date | DATE | 注册日期 |

## 3. 口径与假设（自动化运行，按下述默认口径执行）

1. **有效消费的定义**
   - `status = 'PAID'`：计入消费金额与订单数（正向）。
   - `status = 'REFUND'`：视为退款，金额从消费总金额中**扣减**，**不计入**订单数。
   - `status = 'CANCEL'`：取消单，**不计入**任何指标。
   - 因此 `consume_amount = SUM(PAID.amount) - SUM(REFUND.amount)`，
     代表用户当月**净消费额**。为便于核查，宽表同时保留
     `paid_amount` / `refund_amount` / `paid_order_cnt` / `refund_order_cnt` 明细列。
   - `order_cnt = PAID 笔数`（即净消费对应的成交订单数）。
2. **自然月**：以 `order_date` 截断到月首 `date_trunc('month', order_date)`，
   宽表中以 `stat_month`(DATE，当月 1 号) + `stat_month_str`(VARCHAR `YYYY-MM`) 表示。
3. **城市**：以用户维表当前 `city` 为准（缓慢变化维 SCD-1，覆盖最新值）。
   订单中存在但维表缺失的用户，`city` 置为 `'未知'`。
4. **NULL 状态**：`status IS NULL` 按无效单处理，不计入指标。
5. **金额非负**：源订单 `amount` 约定非负；DDL 中订单金额已 NOT NULL。

## 4. 分层与数据流

```
ods_orders ─┐
            ├─► (01) dwd_orders_clean   清洗/打标，过滤 CANCEL/NULL
ods_users ──┘            │
                         ├─► (02) 按 user_id + 自然月聚合
                         │         JOIN ods_users 补城市
                         └─► dws_user_consume_monthly  （目标宽表）
                                     │
                                     └─► (03) 数据质量校验
```

- **DWD 层** `dwd_orders_clean`：临时/中间表，仅保留 PAID/REFUND，附带月份字段与正负金额。
- **DWS 层** `dws_user_consume_monthly`：最终宽表。

## 5. 步骤清单（执行顺序）

| 步骤 | 文件 | 作用 | 幂等策略 |
|------|------|------|----------|
| 0 | `00_create_target_table.sql` | 创建目标宽表与 DWD 中间表 | `CREATE TABLE IF NOT EXISTS` |
| 1 | `01_stage_clean_orders.sql` | 清洗订单 → 写入 DWD 中间表 | `TRUNCATE` 后重灌 |
| 2 | `02_build_monthly_wide.sql` | 聚合 + 关联用户 → 写入宽表 | 全量 `TRUNCATE` 重建（见说明支持按月增量） |
| 3 | `03_data_quality_checks.sql` | 行数/主键唯一性/空城市等校验 | 只读查询 |
| - | `run_all.sql` | 顺序串联以上脚本 | - |

## 6. GaussDB 适配说明

- 目标表/中间表建表使用 `DISTRIBUTE BY HASH(user_id)`（分布式部署）。
  若为单机/集中式 GaussDB 或标准 PostgreSQL，可忽略/删除 `DISTRIBUTE BY` 子句。
- `date_trunc`、`to_char`、`COALESCE`、`CTE`、`INSERT ... SELECT` 均为 PG 兼容语法。
- 全量重建采用 `TRUNCATE + INSERT`；如需按月增量，可改为
  `DELETE FROM ... WHERE stat_month = :p_month` 后再 `INSERT`（脚本中给出注释示例）。

## 7. 运行方式

```bash
# 通过 gsql 顺序执行
gsql -d <db> -h <host> -p <port> -U <user> -f run_all.sql
# 或单步执行
gsql -d <db> -f 00_create_target_table.sql
gsql -d <db> -f 01_stage_clean_orders.sql
gsql -d <db> -f 02_build_monthly_wide.sql
gsql -d <db> -f 03_data_quality_checks.sql
```
