CREATE DATABASE IF NOT EXISTS transfer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS account_a DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS account_b DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE transfer;

CREATE TABLE IF NOT EXISTS transfer_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transfer_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    source_account_type VARCHAR(32) NOT NULL,
    target_account_type VARCHAR(32) NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    amount DECIMAL(32, 8) NOT NULL,
    transfer_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(512),
    version BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_transfer_order_transfer_id (transfer_id),
    KEY idx_transfer_order_status_updated (status, updated_at),
    KEY idx_transfer_order_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transfer_step_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transfer_id VARCHAR(64) NOT NULL,
    step_name VARCHAR(64) NOT NULL,
    step_status VARCHAR(32) NOT NULL,
    request_body LONGTEXT,
    response_body LONGTEXT,
    error_message VARCHAR(512),
    created_at DATETIME NOT NULL,
    KEY idx_transfer_step_log_transfer (transfer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE account_a;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO account_balance
    (user_id, asset_code, available_amount, frozen_amount, version, created_at, updated_at)
VALUES
    ('user-1', 'USDT', 1000.00000000, 0.00000000, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    available_amount = VALUES(available_amount),
    frozen_amount = VALUES(frozen_amount),
    updated_at = NOW();

USE account_b;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO account_balance
    (user_id, asset_code, available_amount, frozen_amount, version, created_at, updated_at)
VALUES
    ('user-1', 'USDT', 500.00000000, 0.00000000, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    available_amount = VALUES(available_amount),
    frozen_amount = VALUES(frozen_amount),
    updated_at = NOW();
