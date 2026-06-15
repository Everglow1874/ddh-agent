-- 交易域源表结构(GaussDB / PG 兼容)

-- 订单表
CREATE TABLE ods_orders (
    order_id    BIGINT        NOT NULL,  -- 订单ID
    user_id     BIGINT        NOT NULL,  -- 用户ID
    order_date  DATE          NOT NULL,  -- 下单日期
    amount      DECIMAL(12,2) NOT NULL,  -- 订单金额
    status      VARCHAR(20)              -- 订单状态 PAID/REFUND/CANCEL
);

-- 用户表
CREATE TABLE ods_users (
    user_id    BIGINT      NOT NULL,  -- 用户ID
    user_name  VARCHAR(50),           -- 用户名
    city       VARCHAR(50),           -- 城市
    reg_date   DATE                   -- 注册日期
);
