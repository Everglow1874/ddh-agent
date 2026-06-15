-- =============================================================
-- 步骤 0：创建目标宽表 + DWD 中间表
-- 目标库：GaussDB (PostgreSQL 兼容)
-- 幂等：CREATE TABLE IF NOT EXISTS
-- =============================================================

-- -------------------------------------------------------------
-- DWD 中间表：清洗后的有效订单（PAID / REFUND），附带月份与有符号金额
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dwd_orders_clean (
    order_id     BIGINT        NOT NULL,           -- 订单 ID
    user_id      BIGINT        NOT NULL,           -- 用户 ID
    order_date   DATE          NOT NULL,           -- 下单日期
    stat_month   DATE          NOT NULL,           -- 自然月（当月 1 号）
    status       VARCHAR(20)   NOT NULL,           -- PAID / REFUND
    amount       DECIMAL(12,2) NOT NULL,           -- 原始订单金额（非负）
    signed_amount DECIMAL(12,2) NOT NULL,          -- 有符号金额：PAID 为 +amount，REFUND 为 -amount
    is_paid      SMALLINT      NOT NULL,           -- 是否成交订单 1/0
    is_refund    SMALLINT      NOT NULL            -- 是否退款 1/0
)
-- GaussDB 分布式部署：按 user_id 哈希分布；集中式/PG 可删除此行
DISTRIBUTE BY HASH (user_id)
;

-- -------------------------------------------------------------
-- DWS 目标宽表：用户 × 自然月 消费宽表
-- 主键粒度：(user_id, stat_month)
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dws_user_consume_monthly (
    user_id          BIGINT        NOT NULL,       -- 用户 ID
    stat_month       DATE          NOT NULL,       -- 自然月（当月 1 号）
    stat_month_str   VARCHAR(7)    NOT NULL,       -- 自然月字符串 YYYY-MM
    user_name        VARCHAR(50),                  -- 用户名（维表冗余）
    city             VARCHAR(50)   NOT NULL,       -- 所在城市（缺失填 '未知'）
    consume_amount   DECIMAL(14,2) NOT NULL,       -- 消费总金额（净额 = PAID - REFUND）
    order_cnt        BIGINT        NOT NULL,       -- 订单数（PAID 笔数）
    -- ---- 以下为便于核查保留的明细列 ----
    paid_amount      DECIMAL(14,2) NOT NULL,       -- PAID 金额合计
    refund_amount    DECIMAL(14,2) NOT NULL,       -- REFUND 金额合计
    paid_order_cnt   BIGINT        NOT NULL,       -- PAID 笔数
    refund_order_cnt BIGINT        NOT NULL,       -- REFUND 笔数
    etl_time         TIMESTAMP     NOT NULL DEFAULT now()  -- 加工时间
)
-- GaussDB 分布式部署：按 user_id 哈希分布；集中式/PG 可删除此行
DISTRIBUTE BY HASH (user_id)
;

-- 说明：GaussDB 行存表不强制支持主键约束在分布式场景，
-- 这里通过 ETL 聚合保证 (user_id, stat_month) 唯一，并在步骤 3 中做唯一性校验。
