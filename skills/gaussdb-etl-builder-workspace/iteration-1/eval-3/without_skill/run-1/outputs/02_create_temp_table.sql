-- =============================================================
-- Step 2: 创建当日临时汇总表
-- 表名带 ${TX_DATE_NODASH} 后缀,实现不同业务日跑批隔离
-- 先 DROP 再 CREATE,保证本步骤可重复执行
-- =============================================================

DROP TABLE IF EXISTS tmp_trade_daily_${TX_DATE_NODASH};

CREATE TABLE tmp_trade_daily_${TX_DATE_NODASH} (
    stat_date      DATE           NOT NULL,
    channel        VARCHAR(20)    NOT NULL,
    total_cnt      BIGINT         NOT NULL,
    total_amount   DECIMAL(18,2)  NOT NULL,
    pay_cnt        BIGINT         NOT NULL,
    pay_amount     DECIMAL(18,2)  NOT NULL,
    refund_cnt     BIGINT         NOT NULL,
    refund_amount  DECIMAL(18,2)  NOT NULL,
    net_amount     DECIMAL(18,2)  NOT NULL
);
