# 数据域知识文档:交易域(订单/用户)

> 本文档沉淀**已确认**的可复用知识,跨会话累积。每次会话经用户同意后增量更新,不覆盖既有内容。

## 表清单

### 表:ods_orders

- **用途**:订单事实表,记录每笔订单的金额与状态。
- **字段**:
  - `order_id` `BIGINT` —— 订单ID(主键)
  - `user_id` `BIGINT` —— 下单用户ID
  - `order_date` `DATE` —— 下单日期
  - `amount` `DECIMAL(12,2)` —— 订单金额
  - `status` `VARCHAR(20)` —— 订单状态,取值 `PAID/REFUND/CANCEL`
- **备注**:消费类统计通常仅取 `status = 'PAID'`。

### 表:ods_users

- **用途**:用户维度表,记录用户基本属性。
- **字段**:
  - `user_id` `BIGINT` —— 用户ID(主键)
  - `user_name` `VARCHAR(50)` —— 用户名
  - `city` `VARCHAR(50)` —— 所在城市
  - `reg_date` `DATE` —— 注册日期

## 关联关系

> 形如 `A.col ↔ B.col  [基数]  说明`。基数取 `1:1` 或 `1:N`(N 在多的一侧)。

- `ods_users.user_id` ↔ `ods_orders.user_id`  `1:N`  一个用户对应多笔订单

## 字段口径 / 枚举(可选)

- `ods_orders.status` 取值:`PAID=已支付`,`REFUND=已退款`,`CANCEL=已取消`
- 「消费总金额 / 消费订单数」计算口径:仅统计 `status = 'PAID'` 的订单
- 「自然月」口径:`to_char(order_date, 'YYYY-MM')`
