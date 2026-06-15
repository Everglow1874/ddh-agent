-- ============================================================
-- Step 1: 创建目标表 rpt_user_monthly_consumption
-- 口径：每个用户每个自然月一行，记录当月消费总金额、订单数、所在城市
-- ============================================================
CREATE TABLE IF NOT EXISTS rpt_user_monthly_consumption (
    user_id      BIGINT        NOT NULL,  -- 用户ID
    stat_month   VARCHAR(7)    NOT NULL,  -- 统计月份，格式 YYYY-MM（自然月）
    city         VARCHAR(50),             -- 用户所在城市（来自用户表）
    total_amount DECIMAL(14,2) NOT NULL,  -- 当月消费总金额（仅 PAID 订单合计）
    order_cnt    BIGINT        NOT NULL   -- 当月消费订单数（仅 PAID 订单计数）
);
