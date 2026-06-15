-- =============================================================================
-- 目标表 DDL：区域月度销售汇总表
-- 数据库：GaussDB（openGauss，PostgreSQL 兼容语法）
-- 层级：DWS（汇总层）
-- 粒度：区域 + 自然月（每个 region_code + stat_month 一行）
-- =============================================================================

DROP TABLE IF EXISTS dws_region_month_sales_summary;

CREATE TABLE dws_region_month_sales_summary (
    region_code         VARCHAR(32)     NOT NULL,                    -- 区域编码
    region_name         VARCHAR(128),                                -- 区域名称
    stat_month          DATE            NOT NULL,                    -- 统计月份（取当月第 1 天，如 2026-05-01）
    order_cnt           BIGINT          NOT NULL DEFAULT 0,          -- 订单数（去重订单号）
    customer_cnt        BIGINT          NOT NULL DEFAULT 0,          -- 下单客户数（去重）
    product_cnt         BIGINT          NOT NULL DEFAULT 0,          -- 动销商品数（去重）
    total_quantity      NUMERIC(20,2)   NOT NULL DEFAULT 0,          -- 销售总数量
    total_sale_amount   NUMERIC(20,2)   NOT NULL DEFAULT 0,          -- 销售总金额
    total_cost_amount   NUMERIC(20,2)   NOT NULL DEFAULT 0,          -- 销售总成本
    gross_profit        NUMERIC(20,2)   NOT NULL DEFAULT 0,          -- 毛利 = 销售额 - 成本
    gross_profit_rate   NUMERIC(9,4)    NOT NULL DEFAULT 0,          -- 毛利率 = 毛利 / 销售额
    avg_order_amount    NUMERIC(20,2)   NOT NULL DEFAULT 0,          -- 客单价 = 销售额 / 订单数
    etl_time            TIMESTAMP       NOT NULL DEFAULT now(),      -- ETL 加工时间
    CONSTRAINT pk_dws_region_month_sales PRIMARY KEY (region_code, stat_month)
)
-- GaussDB 分布式部署时建议按区域哈希分布；单机版可忽略此子句
DISTRIBUTE BY HASH (region_code);

COMMENT ON TABLE  dws_region_month_sales_summary                   IS '区域月度销售汇总表';
COMMENT ON COLUMN dws_region_month_sales_summary.region_code       IS '区域编码';
COMMENT ON COLUMN dws_region_month_sales_summary.region_name       IS '区域名称';
COMMENT ON COLUMN dws_region_month_sales_summary.stat_month        IS '统计月份（当月第 1 天）';
COMMENT ON COLUMN dws_region_month_sales_summary.order_cnt         IS '订单数（去重）';
COMMENT ON COLUMN dws_region_month_sales_summary.customer_cnt      IS '下单客户数（去重）';
COMMENT ON COLUMN dws_region_month_sales_summary.product_cnt       IS '动销商品数（去重）';
COMMENT ON COLUMN dws_region_month_sales_summary.total_quantity    IS '销售总数量';
COMMENT ON COLUMN dws_region_month_sales_summary.total_sale_amount IS '销售总金额';
COMMENT ON COLUMN dws_region_month_sales_summary.total_cost_amount IS '销售总成本';
COMMENT ON COLUMN dws_region_month_sales_summary.gross_profit      IS '毛利（销售额-成本）';
COMMENT ON COLUMN dws_region_month_sales_summary.gross_profit_rate IS '毛利率';
COMMENT ON COLUMN dws_region_month_sales_summary.avg_order_amount  IS '客单价（销售额/订单数）';
COMMENT ON COLUMN dws_region_month_sales_summary.etl_time          IS 'ETL 加工时间';
