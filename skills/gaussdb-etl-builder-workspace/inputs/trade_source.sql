-- 交易流水源表(GaussDB / PG 兼容)

-- 交易流水明细表(每天产生大量记录,按 tx_date 分区)
CREATE TABLE ods_trade_detail (
    tx_id      BIGINT        NOT NULL,  -- 流水ID
    tx_date    DATE          NOT NULL,  -- 交易日期
    user_id    BIGINT        NOT NULL,  -- 用户ID
    channel    VARCHAR(20),             -- 交易渠道
    amount     DECIMAL(12,2) NOT NULL,  -- 交易金额
    tx_type    VARCHAR(20)   NOT NULL   -- 交易类型 PAY/REFUND
);
