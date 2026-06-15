# ETL 作业计划:交易流水 → 每日交易汇总(T+1 增量)

## 1. 业务目标

把交易流水明细表 `ods_trade_detail` 按天增量汇总成一张**每日交易汇总表**
`dws_trade_daily_summary`,按 **交易日期 + 渠道** 统计交易金额和笔数。

- 调度方式:每天 T+1 跑一次。
- 增量口径:每次只处理上一自然日(`${TX_DATE}`)产生的流水,不做全表扫描。
- 幂等性:同一个 `${TX_DATE}` 可重复执行,结果不重复、不残留(支持失败重跑 / 补数)。

## 2. 平台变量(执行期由平台替换,SQL 中原样保留)

| 变量 | 含义 | 本作业用途 |
|---|---|---|
| `${TX_DATE}` | 执行日的前一天 `YYYY-MM-DD` | 增量过滤 `WHERE tx_date = '${TX_DATE}'`、汇总目标日期 |
| `${TX_DATE_NODASH}` | `${TX_DATE}` 无横杠 `YYYYMMDD` | 临时表命名后缀,避免并发跑批冲突 |
| `${RUN_DATE}` | 执行当天 `YYYY-MM-DD` | 记录加工时间 `etl_date` |

## 3. 源表与目标表

### 源表 `ods_trade_detail`(已存在)

| 字段 | 类型 | 说明 |
|---|---|---|
| tx_id | BIGINT | 流水ID |
| tx_date | DATE | 交易日期(分区键) |
| user_id | BIGINT | 用户ID |
| channel | VARCHAR(20) | 交易渠道(可空) |
| amount | DECIMAL(12,2) | 交易金额 |
| tx_type | VARCHAR(20) | 交易类型 PAY/REFUND |

### 目标表 `dws_trade_daily_summary`(本作业创建)

粒度:`(stat_date, channel)` 一行。统计口径区分支付(PAY)与退款(REFUND):

| 字段 | 类型 | 说明 |
|---|---|---|
| stat_date | DATE | 统计日期 = tx_date |
| channel | VARCHAR(20) | 交易渠道(NULL 归一化为 `UNKNOWN`) |
| total_cnt | BIGINT | 当日该渠道总笔数(含 PAY+REFUND) |
| total_amount | DECIMAL(18,2) | 当日该渠道总金额(各笔 amount 绝对值之和) |
| pay_cnt | BIGINT | 支付笔数 |
| pay_amount | DECIMAL(18,2) | 支付金额 |
| refund_cnt | BIGINT | 退款笔数 |
| refund_amount | DECIMAL(18,2) | 退款金额 |
| net_amount | DECIMAL(18,2) | 净额 = 支付金额 − 退款金额 |
| etl_date | DATE | 加工日期 = `${RUN_DATE}` |

> 设计说明:源表 `amount` 为非负金额,交易方向由 `tx_type` 区分。汇总同时给出
> 总额/支付/退款/净额四个口径,既满足"按日期+渠道统计交易金额和笔数"的核心诉求,
> 又便于下游直接使用净额而无需回查明细。

## 4. 处理步骤

| 步骤 | 文件 | 说明 |
|---|---|---|
| 1 | `01_ddl_target_table.sql` | 创建目标汇总表(`IF NOT EXISTS`,首次/重复执行均安全) |
| 2 | `02_create_temp_table.sql` | 创建当日临时汇总表 `tmp_trade_daily_${TX_DATE_NODASH}` |
| 3 | `03_load_temp_table.sql` | 仅扫描 `tx_date = '${TX_DATE}'` 的增量数据,聚合写入临时表 |
| 4 | `04_merge_into_target.sql` | 先删目标表当日数据再插入(delete+insert 保证幂等),并清理临时表 |
| 5 | `05_validation.sql` | 跑后校验:明细笔数 vs 汇总笔数一致性、是否有重复粒度(可选执行) |

执行顺序:01 → 02 → 03 → 04 →(05 校验)。

## 5. 幂等与增量策略

- **增量**:所有读取都加 `WHERE tx_date = '${TX_DATE}'`,只碰当天分区,不全表扫描。
- **幂等**:步骤 4 采用 "DELETE 目标表 `stat_date = '${TX_DATE}'` → INSERT" 的覆盖式写入。
  同一日重跑会先清掉旧结果再写入,避免重复累加。
- **临时表隔离**:临时表名带 `${TX_DATE_NODASH}` 后缀,不同业务日并发跑批互不影响,
  用完即 `DROP`。

## 6. 边界与约定

- `channel` 可为 NULL,统一 `COALESCE(channel, 'UNKNOWN')` 后再分组,避免 NULL 分组丢失。
- 未知 `tx_type`(非 PAY/REFUND)只计入 `total_cnt/total_amount`,不计入支付/退款拆分,
  通过 05 校验可发现脏数据。
- 金额累加用 `DECIMAL(18,2)` 防止多笔求和溢出 `DECIMAL(12,2)`。
