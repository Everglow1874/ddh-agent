# 数据域知识文档:交易数据域

> 本文档沉淀**已确认**的可复用知识,跨会话累积。每次会话经用户同意后增量更新,不覆盖既有内容。

## 表清单

### 表:ods_trade_detail

- **用途**:交易流水明细表(ODS 层),记录每笔交易明细,数据量大,按 `tx_date` 分区。
- **字段**:
  - `tx_id` `BIGINT` —— 流水ID
  - `tx_date` `DATE` —— 交易日期(分区键,T+1 增量过滤键)
  - `user_id` `BIGINT` —— 用户ID
  - `channel` `VARCHAR(20)` —— 交易渠道,**可空**(汇总时空值归一为 `'UNKNOWN'`)
  - `amount` `DECIMAL(12,2)` —— 交易金额
  - `tx_type` `VARCHAR(20)` —— 交易类型,枚举 `PAY` / `REFUND`
- **备注**:每天产生大量记录;T+1 增量作业只处理 `${TX_DATE}` 当天数据。

### 表:rpt_trade_daily_summary

- **用途**:每日交易汇总报表表(RPT 层),按 交易日期 + 渠道 汇总金额与笔数。
- **字段**:
  - `stat_date` `DATE` —— 统计日期(= `ods_trade_detail.tx_date`)
  - `channel` `VARCHAR(20)` —— 交易渠道(空值归一为 `'UNKNOWN'`)
  - `total_amount` `DECIMAL(18,2)` —— 当日该渠道交易总金额
  - `tx_cnt` `BIGINT` —— 当日该渠道交易笔数
  - `etl_date` `DATE` —— 加工日期(`${RUN_DATE}`)
- **备注**:由 `ods_trade_detail` T+1 增量加工而来;按 `stat_date` 增量、可幂等重跑。

## 关联关系

- `rpt_trade_daily_summary.stat_date` ↔ `ods_trade_detail.tx_date`  `1:N`  汇总表一天一渠道一行,明细表一天多条流水。

## 字段口径 / 枚举

- `ods_trade_detail.tx_type` 取值:`PAY=支付`,`REFUND=退款`。
- 当前每日汇总口径:**不区分** `tx_type`,对当日同渠道所有流水求 `SUM(amount)` 与 `COUNT(*)`。
- `channel` 空值口径:归一为 `'UNKNOWN'` 参与分组。
