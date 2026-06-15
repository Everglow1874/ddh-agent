-- =============================================================
-- Step 3: 增量聚合写入临时表
-- 只扫描 tx_date = '${TX_DATE}' 当天的数据(T+1 增量,不全表扫描)
-- 按 (tx_date, channel) 分组,channel 为 NULL 归一化为 'UNKNOWN'
-- =============================================================

INSERT INTO tmp_trade_daily_${TX_DATE_NODASH} (
    stat_date,
    channel,
    total_cnt,
    total_amount,
    pay_cnt,
    pay_amount,
    refund_cnt,
    refund_amount,
    net_amount
)
SELECT
    tx_date                                                         AS stat_date,
    COALESCE(channel, 'UNKNOWN')                                    AS channel,
    COUNT(*)                                                        AS total_cnt,
    SUM(amount)                                                     AS total_amount,
    COUNT(*) FILTER (WHERE tx_type = 'PAY')                         AS pay_cnt,
    COALESCE(SUM(amount) FILTER (WHERE tx_type = 'PAY'), 0)         AS pay_amount,
    COUNT(*) FILTER (WHERE tx_type = 'REFUND')                      AS refund_cnt,
    COALESCE(SUM(amount) FILTER (WHERE tx_type = 'REFUND'), 0)      AS refund_amount,
    COALESCE(SUM(amount) FILTER (WHERE tx_type = 'PAY'), 0)
        - COALESCE(SUM(amount) FILTER (WHERE tx_type = 'REFUND'), 0) AS net_amount
FROM ods_trade_detail
WHERE tx_date = '${TX_DATE}'
GROUP BY tx_date, COALESCE(channel, 'UNKNOWN');
