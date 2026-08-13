SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+00:00';

CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT chk_sys_user_username_length
        CHECK (CHAR_LENGTH(username) BETWEEN 3 AND 50),
    CONSTRAINT chk_sys_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Registered platform users';

CREATE TABLE account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    account_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_account_account_no UNIQUE (account_no),
    INDEX idx_account_user_status (user_id, status),
    CONSTRAINT fk_account_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_account_balance_non_negative
        CHECK (balance >= 0.00),
    CONSTRAINT chk_account_currency
        CHECK (currency = 'CNY'),
    CONSTRAINT chk_account_status
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Current account balance and state';

CREATE TABLE transfer_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    transfer_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    initiator_user_id BIGINT UNSIGNED NOT NULL,
    from_account_id BIGINT UNSIGNED NOT NULL,
    to_account_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    remark VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_transfer_order_transfer_no UNIQUE (transfer_no),
    INDEX idx_transfer_initiator_created (initiator_user_id, created_at, id),
    INDEX idx_transfer_from_created (from_account_id, created_at, id),
    INDEX idx_transfer_to_created (to_account_id, created_at, id),
    CONSTRAINT fk_transfer_initiator
        FOREIGN KEY (initiator_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_transfer_from_account
        FOREIGN KEY (from_account_id) REFERENCES account (id),
    CONSTRAINT fk_transfer_to_account
        FOREIGN KEY (to_account_id) REFERENCES account (id),
    CONSTRAINT chk_transfer_accounts_different
        CHECK (from_account_id <> to_account_id),
    CONSTRAINT chk_transfer_amount_positive
        CHECK (amount > 0.00),
    CONSTRAINT chk_transfer_currency
        CHECK (currency = 'CNY'),
    CONSTRAINT chk_transfer_status
        CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'One business order per transfer request';

CREATE TABLE transaction_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY',
    balance_before DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    counterparty_account_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_transaction_record_no UNIQUE (record_no),
    CONSTRAINT uk_transaction_business_account_direction
        UNIQUE (business_type, business_id, account_id, direction),
    INDEX idx_transaction_account_created (account_id, created_at, id),
    INDEX idx_transaction_user_created (user_id, created_at, id),
    CONSTRAINT fk_transaction_account
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_transaction_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_transaction_counterparty
        FOREIGN KEY (counterparty_account_id) REFERENCES account (id),
    CONSTRAINT chk_transaction_business_type
        CHECK (business_type IN ('RECHARGE', 'TRANSFER')),
    CONSTRAINT chk_transaction_direction
        CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_transaction_amount_positive
        CHECK (amount > 0.00),
    CONSTRAINT chk_transaction_currency
        CHECK (currency = 'CNY'),
    CONSTRAINT chk_transaction_balances_non_negative
        CHECK (balance_before >= 0.00 AND balance_after >= 0.00),
    CONSTRAINT chk_transaction_balance_change
        CHECK (
            (direction = 'DEBIT' AND balance_after = balance_before - amount)
            OR
            (direction = 'CREDIT' AND balance_after = balance_before + amount)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Immutable account balance-change journal';

CREATE TABLE idempotency_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    resource_id BIGINT UNSIGNED NULL,
    response_snapshot JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_user_business_key
        UNIQUE (user_id, business_type, idempotency_key),
    INDEX idx_idempotency_status_created (status, created_at, id),
    CONSTRAINT fk_idempotency_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_idempotency_business_type
        CHECK (business_type IN ('TRANSFER')),
    CONSTRAINT chk_idempotency_request_hash_length
        CHECK (CHAR_LENGTH(request_hash) = 64),
    CONSTRAINT chk_idempotency_status
        CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_idempotency_success_resource
        CHECK (status <> 'SUCCESS' OR resource_id IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Database-backed request idempotency claims';
