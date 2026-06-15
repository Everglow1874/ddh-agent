# ETL 作业计划：销售明细表 → 区域月度销售汇总表

## 1. 目标
将销售明细表（ODS 层逐笔流水）加工成**区域 + 月度**粒度的销售汇总表（DWS 层），
支持按区域、按月分析销售额、销量、毛利、客单价等核心指标。

## 2. 源与目标
| 项 | 名称 | 说明 |
|----|------|------|
| 源表 | `ods_sales_detail` | 销售明细（逐笔），结构未提供，按典型字段约定 |
| 临时表 | `tmp_region_month_sales_summary` | 聚合结果暂存，保证幂等覆盖 |
| 目标表 | `dws_region_month_sales_summary` | 区域月度销售汇总，主键 (region_code, stat_month) |

> 注：用户未提供源表建表结构，下方源表字段为合理假设，**接入真实表时需对齐列名**。

### 假设的源表字段
order_id（订单号）、customer_id（客户）、product_id（商品）、region_code/region_name（区域）、
sale_date（销售日期）、quantity（数量）、sale_amount（销售额）、cost_amount（成本）。

## 3. 汇总粒度与指标
- 粒度：`region_code` × `stat_month`（月首日）
- 指标：
  - order_cnt 订单数（去重 order_id）
  - customer_cnt 下单客户数（去重 customer_id）
  - product_cnt 动销商品数（去重 product_id）
  - total_quantity 销售数量
  - total_sale_amount 销售金额
  - total_cost_amount 销售成本
  - gross_profit 毛利 = 销售额 − 成本
  - gross_profit_rate 毛利率 = 毛利 / 销售额（除零保护）
  - avg_order_amount 客单价 = 销售额 / 订单数（除零保护）

## 4. 作业步骤
1. `01_create_target_table.sql` — 建目标表（含主键、注释、哈希分布键）。
2. `02_create_temp_table.sql` — 建会话级临时表。
3. `03_etl_load.sql` — 主加工：
   - 按月截断日期聚合写入临时表；
   - 删除目标表中本次月份旧数据；
   - 从临时表插入目标表；
   - 全程单事务，保证原子性。

## 5. 调度与幂等
- **加工窗口**：默认加工“上一自然月”。回刷历史时调整 `03_etl_load.sql` 的日期过滤条件。
- **幂等**：按月份「先删后插」，同月重复执行结果一致，可安全重跑。
- **建议调度**：每月 1 号凌晨执行（T+1 加工上月），上游 `ods_sales_detail` 当月数据就绪后触发。

## 6. 数据质量校验（建议）
- 月份维度行数应等于当月有销售的区域数；
- `gross_profit = total_sale_amount − total_cost_amount` 恒等校验；
- 汇总销售额与明细层当月销售额一致性比对。

## 7. GaussDB 适配说明
- 语法基于 openGauss（PostgreSQL 兼容）：`date_trunc`、`CREATE LOCAL TEMPORARY TABLE`、`COMMENT ON`。
- `DISTRIBUTE BY HASH(region_code)` 适用于分布式集群；单机版可移除该子句。
