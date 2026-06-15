-- ============================================================
-- Step 1: 创建目标表 rpt_trade_daily_summary
-- 每日交易汇总表:按 交易日期 + 渠道 统计交易金额与笔数
-- ============================================================
CREATE TABLE IF NOT EXISTS rpt_trade_daily_summary (
    stat_date    DATE          NOT NULL,  -- 统计日期(交易日,取自 ods_trade_detail.tx_date)
    channel      VARCHAR(20)   NOT NULL,  -- 交易渠道(源表 channel 可空,空值归一为 'UNKNOWN')
    total_amount DECIMAL(18,2) NOT NULL,  -- 当日该渠道交易总金额(SUM(amount))
    tx_cnt       BIGINT        NOT NULL,  -- 当日该渠道交易笔数(COUNT(*))
    etl_date     DATE                     -- 加工日期(作业执行当天,'${RUN_DATE}')
);
