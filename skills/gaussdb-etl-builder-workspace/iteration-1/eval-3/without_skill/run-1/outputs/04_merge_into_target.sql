-- =============================================================
-- Step 4: 覆盖式写入目标表(delete + insert,保证幂等)
-- 1) 删除目标表中当日(${TX_DATE})已有结果,支持重跑/补数
-- 2) 从临时表插入最新结果,并写入加工日期 ${RUN_DATE}
-- 3) 清理临时表
-- 建议在同一事务中执行 DELETE + INSERT,避免中间态。
-- =============================================================

BEGIN;

-- 1) 清掉当日旧结果(幂等)
DELETE FROM dws_trade_daily_summary
WHERE stat_date = '${TX_DATE}';

-- 2) 写入当日最新汇总结果
INSERT INTO dws_trade_daily_summary (
    stat_date,
    channel,
    total_cnt,
    total_amount,
    pay_cnt,
    pay_amount,
    refund_cnt,
    refund_amount,
    net_amount,
    etl_date
)
SELECT
    stat_date,
    channel,
    total_cnt,
    total_amount,
    pay_cnt,
    pay_amount,
    refund_cnt,
    refund_amount,
    net_amount,
    '${RUN_DATE}'  AS etl_date
FROM tmp_trade_daily_${TX_DATE_NODASH};

COMMIT;

-- 3) 清理当日临时表
DROP TABLE IF EXISTS tmp_trade_daily_${TX_DATE_NODASH};
