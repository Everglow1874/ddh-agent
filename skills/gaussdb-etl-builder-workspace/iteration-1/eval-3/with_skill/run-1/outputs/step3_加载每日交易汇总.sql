-- ============================================================
-- Step 3: 加载每日交易汇总数据
-- 仅处理 ${TX_DATE}(T+1,执行日前一天)当天的交易流水,
-- 按 交易日期 + 渠道 汇总金额与笔数后写入目标表。
-- ============================================================
INSERT INTO rpt_trade_daily_summary (
    stat_date,
    channel,
    total_amount,
    tx_cnt,
    etl_date
)
SELECT
    t.tx_date                       AS stat_date,      -- 交易日期
    COALESCE(t.channel, 'UNKNOWN')  AS channel,        -- 渠道(空值归一)
    SUM(t.amount)                   AS total_amount,   -- 交易总金额
    COUNT(*)                        AS tx_cnt,         -- 交易笔数
    '${RUN_DATE}'                   AS etl_date        -- 加工日期(执行当天)
FROM ods_trade_detail t
WHERE t.tx_date = '${TX_DATE}'                          -- T+1 增量:只扫当天数据
GROUP BY
    t.tx_date,
    COALESCE(t.channel, 'UNKNOWN');
