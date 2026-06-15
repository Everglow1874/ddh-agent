-- =============================================================================
-- ETL 主作业：销售明细表 -> 区域月度销售汇总表
-- 数据库：GaussDB（openGauss）
-- 模式：增量按月覆盖，幂等可重跑（同一月份重复执行结果一致）
-- =============================================================================
--
-- 源表假设（未提供建表结构，按典型销售明细表约定，请按真实表结构调整列名）：
--   源表名：ods_sales_detail（销售明细表，ODS 层）
--   关键字段：
--     order_id      VARCHAR    订单号
--     customer_id   VARCHAR    客户ID
--     product_id    VARCHAR    商品ID
--     region_code   VARCHAR    区域编码
--     region_name   VARCHAR    区域名称
--     sale_date     DATE/TIMESTAMP 销售日期
--     quantity      NUMERIC    销售数量
--     sale_amount   NUMERIC    销售金额（含税或不含税按业务口径）
--     cost_amount   NUMERIC    销售成本
--
-- 运行参数：用变量控制加工的月份范围（闭区间，按月对齐）。
--   默认加工“上一自然月”。如需全量回刷，自行调整 v_start_month / v_end_month。
-- =============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- ---------------------------------------------------------------------------
-- 1) 聚合到临时表
--    - sale_date 截断到月首日作为 stat_month
--    - 过滤无区域 / 无金额的脏数据（按需调整）
-- ---------------------------------------------------------------------------
TRUNCATE TABLE tmp_region_month_sales_summary;

INSERT INTO tmp_region_month_sales_summary (
    region_code, region_name, stat_month,
    order_cnt, customer_cnt, product_cnt,
    total_quantity, total_sale_amount, total_cost_amount,
    gross_profit, gross_profit_rate, avg_order_amount
)
SELECT
    s.region_code,
    MAX(s.region_name)                                       AS region_name,
    date_trunc('month', s.sale_date)::date                   AS stat_month,
    COUNT(DISTINCT s.order_id)                               AS order_cnt,
    COUNT(DISTINCT s.customer_id)                            AS customer_cnt,
    COUNT(DISTINCT s.product_id)                             AS product_cnt,
    COALESCE(SUM(s.quantity), 0)                             AS total_quantity,
    COALESCE(SUM(s.sale_amount), 0)                          AS total_sale_amount,
    COALESCE(SUM(s.cost_amount), 0)                          AS total_cost_amount,
    COALESCE(SUM(s.sale_amount), 0) - COALESCE(SUM(s.cost_amount), 0) AS gross_profit,
    -- 毛利率：销售额为 0 时记 0，避免除零
    CASE WHEN COALESCE(SUM(s.sale_amount), 0) = 0 THEN 0
         ELSE ROUND((SUM(s.sale_amount) - SUM(s.cost_amount)) / SUM(s.sale_amount), 4)
    END                                                      AS gross_profit_rate,
    -- 客单价：订单数为 0 时记 0
    CASE WHEN COUNT(DISTINCT s.order_id) = 0 THEN 0
         ELSE ROUND(SUM(s.sale_amount) / COUNT(DISTINCT s.order_id), 2)
    END                                                      AS avg_order_amount
FROM ods_sales_detail s
WHERE s.sale_date IS NOT NULL
  AND s.region_code IS NOT NULL
  -- 加工月份范围（默认上一自然月）：
  AND s.sale_date >= date_trunc('month', current_date - INTERVAL '1 month')
  AND s.sale_date <  date_trunc('month', current_date)
GROUP BY s.region_code, date_trunc('month', s.sale_date)::date;

-- ---------------------------------------------------------------------------
-- 2) 删除目标表中本次加工月份的旧数据（幂等关键步骤）
-- ---------------------------------------------------------------------------
DELETE FROM dws_region_month_sales_summary t
WHERE EXISTS (
    SELECT 1 FROM tmp_region_month_sales_summary tmp
    WHERE tmp.stat_month = t.stat_month
);

-- ---------------------------------------------------------------------------
-- 3) 写入目标表
-- ---------------------------------------------------------------------------
INSERT INTO dws_region_month_sales_summary (
    region_code, region_name, stat_month,
    order_cnt, customer_cnt, product_cnt,
    total_quantity, total_sale_amount, total_cost_amount,
    gross_profit, gross_profit_rate, avg_order_amount, etl_time
)
SELECT
    region_code, region_name, stat_month,
    order_cnt, customer_cnt, product_cnt,
    total_quantity, total_sale_amount, total_cost_amount,
    gross_profit, gross_profit_rate, avg_order_amount, now()
FROM tmp_region_month_sales_summary;

COMMIT;

-- ---------------------------------------------------------------------------
-- 4) 加工结果校验（可选，单独执行）
-- ---------------------------------------------------------------------------
-- SELECT stat_month, COUNT(*) AS region_rows,
--        SUM(total_sale_amount) AS month_sale_amount
-- FROM dws_region_month_sales_summary
-- GROUP BY stat_month
-- ORDER BY stat_month;
