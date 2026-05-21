CREATE TABLE IF NOT EXISTS account_balance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    available_amount DECIMAL(32, 8) NOT NULL DEFAULT 0,
    frozen_amount DECIMAL(32, 8) NOT NULL DEFAULT 0,
    version BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_account_balance_user_asset (user_id, asset_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS asset_operation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transfer_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    amount DECIMAL(32, 8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_code VARCHAR(32) NOT NULL,
    response_message VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_asset_operation_transfer_type (transfer_id, operation_type),
    KEY idx_asset_operation_user_asset (user_id, asset_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS finance_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transfer_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    available_delta DECIMAL(32, 8) NOT NULL,
    frozen_delta DECIMAL(32, 8) NOT NULL,
    available_after DECIMAL(32, 8) NOT NULL,
    frozen_after DECIMAL(32, 8) NOT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_finance_ledger_transfer (transfer_id),
    KEY idx_finance_ledger_user_asset (user_id, asset_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
