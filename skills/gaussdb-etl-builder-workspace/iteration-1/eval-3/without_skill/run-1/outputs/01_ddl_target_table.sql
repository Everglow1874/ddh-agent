-- =============================================================
-- Step 1: 创建每日交易汇总目标表(幂等,IF NOT EXISTS)
-- 粒度: (stat_date, channel) 一行
-- =============================================================

CREATE TABLE IF NOT EXISTS dws_trade_daily_summary (
    stat_date      DATE           NOT NULL,                 -- 统计日期 = tx_date
    channel        VARCHAR(20)    NOT NULL,                 -- 交易渠道(NULL 归一化为 UNKNOWN)
    total_cnt      BIGINT         NOT NULL DEFAULT 0,       -- 当日该渠道总笔数(PAY+REFUND+其它)
    total_amount   DECIMAL(18,2)  NOT NULL DEFAULT 0,       -- 当日该渠道总金额
    pay_cnt        BIGINT         NOT NULL DEFAULT 0,       -- 支付笔数
    pay_amount     DECIMAL(18,2)  NOT NULL DEFAULT 0,       -- 支付金额
    refund_cnt     BIGINT         NOT NULL DEFAULT 0,       -- 退款笔数
    refund_amount  DECIMAL(18,2)  NOT NULL DEFAULT 0,       -- 退款金额
    net_amount     DECIMAL(18,2)  NOT NULL DEFAULT 0,       -- 净额 = 支付金额 - 退款金额
    etl_date       DATE           NOT NULL,                 -- 加工日期 = ${RUN_DATE}
    CONSTRAINT pk_dws_trade_daily_summary PRIMARY KEY (stat_date, channel)
)
DISTRIBUTE BY HASH (stat_date);   -- GaussDB 分布式建表;单机/PG 环境可忽略此行

COMMENT ON TABLE dws_trade_daily_summary IS '每日交易汇总表(按日期+渠道,T+1 增量)';
