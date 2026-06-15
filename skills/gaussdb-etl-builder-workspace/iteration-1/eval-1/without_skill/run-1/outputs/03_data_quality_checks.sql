-- =============================================================
-- 步骤 3：数据质量校验（只读）
-- 每条 SQL 返回一个检查项；理想结果在注释中标注。
-- =============================================================

-- [1] 宽表总行数（应 = DWD 中 (user_id, stat_month) 去重组合数）
SELECT 'wide_row_count' AS check_item, COUNT(*) AS val
FROM dws_user_consume_monthly;

-- [2] 主键 (user_id, stat_month) 唯一性：dup_cnt 必须为 0
SELECT 'pk_duplicate_groups' AS check_item, COUNT(*) AS val
FROM (
    SELECT user_id, stat_month
    FROM dws_user_consume_monthly
    GROUP BY user_id, stat_month
    HAVING COUNT(*) > 1
) t;

-- [3] 行数一致性：宽表行数 = DWD 去重组合数（diff 必须为 0）
SELECT 'rowcount_diff_vs_dwd' AS check_item,
       ( (SELECT COUNT(*) FROM dws_user_consume_monthly)
         - (SELECT COUNT(*) FROM (
               SELECT DISTINCT user_id, stat_month FROM dwd_orders_clean
           ) d ) ) AS val;

-- [4] 金额平衡：宽表净消费合计 = DWD 有符号金额合计（diff 必须为 0）
SELECT 'amount_balance_diff' AS check_item,
       ( COALESCE((SELECT SUM(consume_amount) FROM dws_user_consume_monthly), 0)
         - COALESCE((SELECT SUM(signed_amount) FROM dwd_orders_clean), 0) ) AS val;

-- [5] 订单数平衡：宽表 order_cnt 合计 = DWD PAID 笔数（diff 必须为 0）
SELECT 'ordercnt_balance_diff' AS check_item,
       ( COALESCE((SELECT SUM(order_cnt) FROM dws_user_consume_monthly), 0)
         - COALESCE((SELECT SUM(is_paid) FROM dwd_orders_clean), 0) ) AS val;

-- [6] 空城市 NULL 检查：city 不应为 NULL（已用 '未知' 兜底，应为 0）
SELECT 'null_city_count' AS check_item, COUNT(*) AS val
FROM dws_user_consume_monthly
WHERE city IS NULL;

-- [7] 维表缺失用户监控（落为 '未知' 城市的行数，告警类指标，非阻断）
SELECT 'unknown_city_count' AS check_item, COUNT(*) AS val
FROM dws_user_consume_monthly
WHERE city = '未知';

-- [8] 抽样预览
SELECT user_id, stat_month_str, city, consume_amount, order_cnt,
       paid_amount, refund_amount, paid_order_cnt, refund_order_cnt
FROM dws_user_consume_monthly
ORDER BY stat_month, user_id
LIMIT 50;
