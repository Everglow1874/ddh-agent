-- =============================================================
-- 步骤 1：清洗订单 → DWD 中间表 dwd_orders_clean
--   - 仅保留 status IN ('PAID','REFUND')，过滤 CANCEL 与 NULL
--   - 计算自然月 stat_month
--   - 计算有符号金额：PAID 为 +amount，REFUND 为 -amount
-- 幂等：先 TRUNCATE 再全量灌入
-- =============================================================

TRUNCATE TABLE dwd_orders_clean;

INSERT INTO dwd_orders_clean (
    order_id,
    user_id,
    order_date,
    stat_month,
    status,
    amount,
    signed_amount,
    is_paid,
    is_refund
)
SELECT
    o.order_id,
    o.user_id,
    o.order_date,
    date_trunc('month', o.order_date)::date          AS stat_month,
    o.status,
    o.amount,
    CASE
        WHEN o.status = 'PAID'   THEN  o.amount
        WHEN o.status = 'REFUND' THEN -o.amount
        ELSE 0
    END                                              AS signed_amount,
    CASE WHEN o.status = 'PAID'   THEN 1 ELSE 0 END  AS is_paid,
    CASE WHEN o.status = 'REFUND' THEN 1 ELSE 0 END  AS is_refund
FROM ods_orders o
WHERE o.status IN ('PAID', 'REFUND')   -- 过滤 CANCEL 与 NULL 状态
;
