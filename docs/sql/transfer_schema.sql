-- transfer 库表结构：转账编排侧的主状态与步骤日志，仅由 transfer-service 写入。

CREATE TABLE IF NOT EXISTS transfer_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    transfer_id VARCHAR(64) NOT NULL COMMENT '转账单业务唯一编号，对外暴露并贯穿全流程',
    user_id VARCHAR(64) NOT NULL COMMENT '发起转账的用户 ID',
    source_account_type VARCHAR(32) NOT NULL COMMENT '源账户类型：A / B',
    target_account_type VARCHAR(32) NOT NULL COMMENT '目标账户类型：A / B',
    asset_code VARCHAR(32) NOT NULL COMMENT '资产代码，如 USDT',
    amount DECIMAL(32, 8) NOT NULL COMMENT '转账金额，统一用 DECIMAL 避免浮点误差',
    transfer_mode VARCHAR(32) NOT NULL COMMENT '转账模式：MANUAL_REVIEW 人工审核 / AUTO_WITHDRAW 站内自动完成',
    status VARCHAR(32) NOT NULL COMMENT '转账单当前状态（状态机），如 WAIT_REVIEW / DEBIT_SUCCESS / CREDIT_FAILED / SUCCESS',
    last_error_code VARCHAR(64) COMMENT '最近一次失败的错误码，无失败时为空',
    last_error_message VARCHAR(512) COMMENT '最近一次失败的错误信息，无失败时为空',
    version BIGINT DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_transfer_order_transfer_id (transfer_id) COMMENT '转账单号唯一，保证创建幂等',
    KEY idx_transfer_order_status_updated (status, updated_at) COMMENT '供失败状态扫描重试按状态 + 时间检索',
    KEY idx_transfer_order_user (user_id) COMMENT '按用户查询转账单'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转账主状态表：记录源/目标账户、金额、模式、当前状态与最后错误';

CREATE TABLE IF NOT EXISTS transfer_step_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    transfer_id VARCHAR(64) NOT NULL COMMENT '所属转账单号',
    step_name VARCHAR(64) NOT NULL COMMENT '步骤名：冻结 / 确认扣减 / 入账 / 解冻等',
    step_status VARCHAR(32) NOT NULL COMMENT '步骤结果：成功 / 失败',
    request_body LONGTEXT COMMENT '该步骤调用账户服务的请求体快照',
    response_body LONGTEXT COMMENT '该步骤的响应体快照',
    error_message VARCHAR(512) COMMENT '步骤失败时的错误信息',
    created_at DATETIME NOT NULL COMMENT '记录时间',
    KEY idx_transfer_step_log_transfer (transfer_id, created_at) COMMENT '按转账单 + 时间回放步骤过程'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转账步骤日志：逐步记录冻结/扣减/入账/解冻的成功或失败，便于排查';
