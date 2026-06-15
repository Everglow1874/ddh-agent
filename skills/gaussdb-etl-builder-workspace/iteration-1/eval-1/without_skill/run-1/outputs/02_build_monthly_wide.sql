-- =============================================================
-- 步骤 2：构建用户月度消费宽表 dws_user_consume_monthly
--   1) 按 (user_id, stat_month) 聚合 DWD 订单
--   2) LEFT JOIN ods_users 补充 user_name / city
-- 幂等：全量 TRUNCATE 重建
--   （按月增量见文末注释示例）
-- =============================================================

TRUNCATE TABLE dws_user_consume_monthly;

INSERT INTO dws_user_consume_monthly (
    user_id,
    stat_month,
    stat_month_str,
    user_name,
    city,
    consume_amount,
    order_cnt,
    paid_amount,
    refund_amount,
    paid_order_cnt,
    refund_order_cnt,
    etl_time
)
WITH agg AS (
    SELECT
        d.user_id,
        d.stat_month,
        SUM(d.signed_amount)                              AS consume_amount,   -- 净消费 = PAID - REFUND
        SUM(d.is_paid)                                    AS order_cnt,         -- 成交订单数
        SUM(CASE WHEN d.is_paid   = 1 THEN d.amount ELSE 0 END) AS paid_amount,
        SUM(CASE WHEN d.is_refund = 1 THEN d.amount ELSE 0 END) AS refund_amount,
        SUM(d.is_paid)                                    AS paid_order_cnt,
        SUM(d.is_refund)                                  AS refund_order_cnt
    FROM dwd_orders_clean d
    GROUP BY d.user_id, d.stat_month
)
SELECT
    a.user_id,
    a.stat_month,
    to_char(a.stat_month, 'YYYY-MM')          AS stat_month_str,
    u.user_name,
    COALESCE(u.city, '未知')                  AS city,
    a.consume_amount,
    a.order_cnt,
    a.paid_amount,
    a.refund_amount,
    a.paid_order_cnt,
    a.refund_order_cnt,
    now()                                     AS etl_time
FROM agg a
LEFT JOIN ods_users u
       ON u.user_id = a.user_id
;

-- -------------------------------------------------------------
-- 【按月增量示例】（默认走全量，如需增量将上方 TRUNCATE/INSERT 替换为：）
--
-- DELETE FROM dws_user_consume_monthly
--  WHERE stat_month = DATE '2024-01-01';   -- :p_month 当月 1 号
--
-- INSERT INTO dws_user_consume_monthly (...)
-- WITH agg AS (
--   SELECT ... FROM dwd_orders_clean d
--    WHERE d.stat_month = DATE '2024-01-01'
--    GROUP BY d.user_id, d.stat_month
-- ) SELECT ... ;
-- -------------------------------------------------------------
