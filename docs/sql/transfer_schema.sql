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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
