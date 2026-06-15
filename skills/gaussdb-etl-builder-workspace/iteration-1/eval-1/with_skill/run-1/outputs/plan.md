# ETL 执行计划

## 需求描述

把订单表（`ods_orders`）和用户表（`ods_users`）加工成一张按月的用户消费宽表：
每个用户每个自然月一行，统计当月的**消费总金额**、**订单数**、以及该用户**所在城市**。

## 口径与假设

- **消费口径**：仅统计 `status = 'PAID'` 的订单（排除 `REFUND` 退款、`CANCEL` 取消）。
- **自然月**：用 `to_char(order_date, 'YYYY-MM')` 取下单日期所在自然月，输出格式 `YYYY-MM`。
- **城市来源**：取自用户表 `ods_users.city`，按 `user_id` 关联（1:1），用 `LEFT JOIN` 避免用户表缺失时丢失消费记录。
- **关联基数**：`ods_users.user_id` ↔ `ods_orders.user_id` 为 `1:N`（一个用户多笔订单）。
- 全量装载（无平台变量文档、需求未指定增量/日期窗口）。

## 目标表

`rpt_user_monthly_consumption`

| 字段 | 类型 | 注释 |
|---|---|---|
| user_id | BIGINT NOT NULL | 用户ID |
| stat_month | VARCHAR(7) NOT NULL | 统计月份 YYYY-MM（自然月） |
| city | VARCHAR(50) | 用户所在城市 |
| total_amount | DECIMAL(14,2) NOT NULL | 当月消费总金额（仅 PAID） |
| order_cnt | BIGINT NOT NULL | 当月消费订单数（仅 PAID） |

## ETL 步骤

### Step 1: 创建目标表

用确认的 schema 创建报表表 `rpt_user_monthly_consumption`（`is_temp_table=false`）。

输出表：`rpt_user_monthly_consumption`

### Step 2: 加载用户按月消费数据

先按 `用户 + 自然月` 聚合订单（仅 `status = 'PAID'`），计算消费总金额与订单数，
再 `LEFT JOIN` 用户表补齐城市，一条 `INSERT ... SELECT`（含 CTE）完成装载，无需临时表。

输出表：`rpt_user_monthly_consumption`
