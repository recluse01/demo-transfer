-- 账户库表结构：余额、操作幂等记录与财务流水。
-- 同一份脚本分别应用到 account_a 与 account_b 两个库（A/B 账户共用同一套结构）。

CREATE TABLE IF NOT EXISTS account_balance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    user_id VARCHAR(64) NOT NULL COMMENT '用户 ID',
    asset_code VARCHAR(32) NOT NULL COMMENT '资产代码，如 USDT',
    available_amount DECIMAL(32, 8) NOT NULL DEFAULT 0 COMMENT '可用金额',
    frozen_amount DECIMAL(32, 8) NOT NULL DEFAULT 0 COMMENT '冻结金额',
    version BIGINT DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_account_balance_user_asset (user_id, asset_code) COMMENT '同一用户同一资产仅一条余额记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资产余额表：记录可用金额与冻结金额';

CREATE TABLE IF NOT EXISTS asset_operation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    transfer_id VARCHAR(64) NOT NULL COMMENT '所属转账单号',
    operation_type VARCHAR(32) NOT NULL COMMENT '操作类型：FREEZE 冻结 / CONFIRM_DEBIT 确认扣减 / CANCEL_FREEZE 解冻 / CREDIT 入账',
    user_id VARCHAR(64) NOT NULL COMMENT '用户 ID',
    asset_code VARCHAR(32) NOT NULL COMMENT '资产代码，如 USDT',
    amount DECIMAL(32, 8) NOT NULL COMMENT '本次操作金额',
    status VARCHAR(16) NOT NULL COMMENT '操作状态：成功 / 失败',
    response_code VARCHAR(32) NOT NULL COMMENT '返回码',
    response_message VARCHAR(255) NOT NULL COMMENT '返回信息',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_asset_operation_transfer_type (transfer_id, operation_type) COMMENT '幂等键：同一转账单同一操作仅生效一次，抵抗超时与重复请求',
    KEY idx_asset_operation_user_asset (user_id, asset_code) COMMENT '按用户 + 资产查询操作记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户操作幂等记录表：以 transfer_id + operation_type 保证每个资产操作只应用一次';

CREATE TABLE IF NOT EXISTS finance_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    transfer_id VARCHAR(64) NOT NULL COMMENT '所属转账单号',
    user_id VARCHAR(64) NOT NULL COMMENT '用户 ID',
    asset_code VARCHAR(32) NOT NULL COMMENT '资产代码，如 USDT',
    operation_type VARCHAR(32) NOT NULL COMMENT '触发本条流水的操作类型',
    available_delta DECIMAL(32, 8) NOT NULL COMMENT '可用金额变动量，正为增、负为减',
    frozen_delta DECIMAL(32, 8) NOT NULL COMMENT '冻结金额变动量，正为增、负为减',
    available_after DECIMAL(32, 8) NOT NULL COMMENT '变动后的可用金额',
    frozen_after DECIMAL(32, 8) NOT NULL COMMENT '变动后的冻结金额',
    created_at DATETIME NOT NULL COMMENT '记录时间',
    KEY idx_finance_ledger_transfer (transfer_id) COMMENT '按转账单查询流水',
    KEY idx_finance_ledger_user_asset (user_id, asset_code) COMMENT '按用户 + 资产查询流水'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务流水表：每次资产变动写一条，记录变动量与变动后余额';
