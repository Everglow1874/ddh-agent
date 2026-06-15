-- =============================================================
-- Step 5: 跑后校验(可选,建议纳入调度的质量检查节点)
-- 全部针对当日 ${TX_DATE},不全表扫描。
-- =============================================================

-- 校验 1:汇总总笔数 == 明细当日笔数(应为 0 差异)
SELECT
    (SELECT COUNT(*) FROM ods_trade_detail WHERE tx_date = '${TX_DATE}')          AS detail_cnt,
    (SELECT COALESCE(SUM(total_cnt), 0) FROM dws_trade_daily_summary
        WHERE stat_date = '${TX_DATE}')                                           AS summary_cnt,
    (SELECT COUNT(*) FROM ods_trade_detail WHERE tx_date = '${TX_DATE}')
        - (SELECT COALESCE(SUM(total_cnt), 0) FROM dws_trade_daily_summary
            WHERE stat_date = '${TX_DATE}')                                       AS diff_cnt;

-- 校验 2:汇总总金额 == 明细当日金额(应为 0 差异)
SELECT
    (SELECT COALESCE(SUM(amount), 0) FROM ods_trade_detail WHERE tx_date = '${TX_DATE}')      AS detail_amount,
    (SELECT COALESCE(SUM(total_amount), 0) FROM dws_trade_daily_summary
        WHERE stat_date = '${TX_DATE}')                                                       AS summary_amount;

-- 校验 3:目标表粒度无重复(每个 (stat_date, channel) 至多一行,应返回 0 行)
SELECT stat_date, channel, COUNT(*) AS dup_cnt
FROM dws_trade_daily_summary
WHERE stat_date = '${TX_DATE}'
GROUP BY stat_date, channel
HAVING COUNT(*) > 1;

-- 校验 4:发现非 PAY/REFUND 的脏类型(计入 total 但未拆分,应返回 0 行)
SELECT tx_type, COUNT(*) AS cnt
FROM ods_trade_detail
WHERE tx_date = '${TX_DATE}'
  AND tx_type NOT IN ('PAY', 'REFUND')
GROUP BY tx_type;
