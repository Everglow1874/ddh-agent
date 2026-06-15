-- =============================================================================
-- 临时表 / 中间表 DDL：区域月度销售汇总（聚合结果暂存）
-- 作用：先把聚合结果落到临时表做校验，再原子覆盖目标表，保证作业可重跑、幂等
-- 说明：使用 GaussDB 本地临时表，会话结束自动清理；DDL 与目标表结构一致（不含约束）
-- =============================================================================

DROP TABLE IF EXISTS tmp_region_month_sales_summary;

CREATE LOCAL TEMPORARY TABLE tmp_region_month_sales_summary (
    region_code         VARCHAR(32),
    region_name         VARCHAR(128),
    stat_month          DATE,
    order_cnt           BIGINT,
    customer_cnt        BIGINT,
    product_cnt         BIGINT,
    total_quantity      NUMERIC(20,2),
    total_sale_amount   NUMERIC(20,2),
    total_cost_amount   NUMERIC(20,2),
    gross_profit        NUMERIC(20,2),
    gross_profit_rate   NUMERIC(9,4),
    avg_order_amount    NUMERIC(20,2)
) ON COMMIT PRESERVE ROWS;
