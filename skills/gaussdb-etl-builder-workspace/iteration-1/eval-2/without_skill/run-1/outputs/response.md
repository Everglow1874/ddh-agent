# 区域月度销售汇总 ETL 作业

已根据你的需求，把**销售明细表**加工成一张**区域月度销售汇总表**，并生成了完整的 GaussDB ETL 作业写入输出目录。

## 一点重要说明
你这次没有附带源表的建表结构，所以我按一张**典型销售明细表**的字段做了合理假设：

| 字段 | 含义 |
|------|------|
| order_id | 订单号 |
| customer_id | 客户ID |
| product_id | 商品ID |
| region_code / region_name | 区域编码 / 名称 |
| sale_date | 销售日期 |
| quantity | 销售数量 |
| sale_amount | 销售金额 |
| cost_amount | 销售成本 |

源表名我用的是 `ods_sales_detail`。**如果你的真实表名或列名不同，把 `03_etl_load.sql` 里的列名对齐一下即可**，其余逻辑通用。

## 产出物（已写入 outputs 目录）
1. **`plan.md`** — 作业计划：粒度、指标口径、步骤、调度与幂等策略。
2. **`01_create_target_table.sql`** — 目标表 `dws_region_month_sales_summary` 建表语句（主键、字段注释、哈希分布键）。
3. **`02_create_temp_table.sql`** — 临时表 `tmp_region_month_sales_summary` 建表语句（聚合暂存，保证幂等覆盖）。
4. **`03_etl_load.sql`** — ETL 主作业：聚合 → 删除本月旧数据 → 写入目标表，全程单事务。

## 汇总设计
- **粒度**：区域 × 自然月（`stat_month` 取当月第 1 天）。
- **指标**：订单数、客户数、动销商品数、销售数量、销售额、成本、毛利、毛利率、客单价。
- **毛利率 / 客单价**都做了**除零保护**（分母为 0 时记 0）。

## 加工与调度
- **加工窗口**：默认加工「上一自然月」，每月 1 号 T+1 跑上月数据；历史回刷只需改 `03_etl_load.sql` 的日期过滤条件。
- **幂等可重跑**：按月份「先删后插」，同一月份重复执行结果一致，作业失败可安全重跑。

## 执行顺序
```bash
gsql -d <db> -f 01_create_target_table.sql   # 首次建表，仅需一次
gsql -d <db> -f 02_create_temp_table.sql      # 临时表（会话级，可随作业一起执行）
gsql -d <db> -f 03_etl_load.sql               # 每次调度执行
```

## GaussDB 适配
SQL 全部基于 openGauss（PostgreSQL 兼容）语法：`date_trunc`、`CREATE LOCAL TEMPORARY TABLE`、`COMMENT ON`。
目标表带 `DISTRIBUTE BY HASH(region_code)`，适用于分布式集群；**单机版可移除该子句**。

需要的话，把真实的销售明细表结构发我，我可以把列名、金额口径（含税/不含税）、以及是否需要按渠道/商品类目进一步下钻一并校准。
