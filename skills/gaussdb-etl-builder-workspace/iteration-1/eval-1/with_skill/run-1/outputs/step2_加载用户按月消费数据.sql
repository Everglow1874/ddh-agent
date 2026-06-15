-- ============================================================
-- Step 2: 加载用户按月消费数据
-- 先按 用户+自然月 聚合订单（仅 PAID），再 LEFT JOIN 用户表补城市，
-- 一条 INSERT ... SELECT 完成，无需临时表。
-- ============================================================
INSERT INTO rpt_user_monthly_consumption (user_id, stat_month, city, total_amount, order_cnt)
WITH
-- 2.1 按用户 + 自然月聚合消费金额与订单数（消费口径：status = 'PAID'）
monthly_agg AS (
    SELECT
        o.user_id,
        to_char(o.order_date, 'YYYY-MM') AS stat_month,
        SUM(o.amount)                    AS total_amount,
        COUNT(*)                         AS order_cnt
    FROM ods_orders o
    WHERE o.status = 'PAID'
    GROUP BY o.user_id, to_char(o.order_date, 'YYYY-MM')
)
-- 2.2 关联用户表补齐所在城市（LEFT JOIN：用户表缺失也不丢消费记录）
SELECT
    m.user_id,
    m.stat_month,
    u.city,
    m.total_amount,
    m.order_cnt
FROM monthly_agg m
LEFT JOIN ods_users u ON m.user_id = u.user_id;
