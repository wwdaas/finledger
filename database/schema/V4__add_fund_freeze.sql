SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+00:00';

ALTER TABLE idempotency_record
    DROP CHECK chk_idempotency_business_type,
    ADD CONSTRAINT chk_idempotency_business_type
        CHECK (business_type IN ('TRANSFER', 'DEFERRED_TRANSFER', 'FUND_FREEZE'));

ALTER TABLE fund_movement_record
    DROP CHECK chk_fund_movement_business_type,
    ADD CONSTRAINT chk_fund_movement_business_type
        CHECK (business_type IN ('DEFERRED_TRANSFER', 'FUND_FREEZE'));

CREATE TABLE fund_freeze (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    freeze_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'FROZEN',
    business_type VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_fund_freeze_no UNIQUE (freeze_no),
    INDEX idx_fund_freeze_user_created (user_id, created_at, id),
    INDEX idx_fund_freeze_account_created (account_id, created_at, id),
    CONSTRAINT fk_fund_freeze_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_fund_freeze_account
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT chk_fund_freeze_amount_positive
        CHECK (amount > 0.00),
    CONSTRAINT chk_fund_freeze_status
        CHECK (status = 'FROZEN'),
    CONSTRAINT chk_fund_freeze_business_type
        CHECK (
            CHAR_LENGTH(business_type) BETWEEN 1 AND 30
            AND business_type REGEXP '^[A-Z][A-Z0-9_]*$'
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Traceable account fund-freeze orders';
