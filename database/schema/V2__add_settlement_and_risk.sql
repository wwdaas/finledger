SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+00:00';

ALTER TABLE account
    ADD COLUMN available_balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00 AFTER balance,
    ADD COLUMN frozen_balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00 AFTER available_balance;

UPDATE account
SET available_balance = balance,
    frozen_balance = 0.00;

ALTER TABLE account
    ADD CONSTRAINT chk_account_available_balance_non_negative
        CHECK (available_balance >= 0.00),
    ADD CONSTRAINT chk_account_frozen_balance_non_negative
        CHECK (frozen_balance >= 0.00),
    ADD CONSTRAINT chk_account_balance_components
        CHECK (balance = available_balance + frozen_balance);

ALTER TABLE transfer_order
    ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT 'IMMEDIATE' AFTER transfer_no,
    ADD COLUMN risk_decision VARCHAR(10) NOT NULL DEFAULT 'PASS' AFTER status,
    DROP CHECK chk_transfer_status,
    ADD CONSTRAINT chk_transfer_order_type
        CHECK (order_type IN ('IMMEDIATE', 'DEFERRED')),
    ADD CONSTRAINT chk_transfer_status
        CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED', 'PENDING', 'SETTLED', 'CANCELLED')),
    ADD CONSTRAINT chk_transfer_risk_decision
        CHECK (risk_decision IN ('PASS', 'REVIEW', 'REJECT'));

ALTER TABLE idempotency_record
    DROP CHECK chk_idempotency_business_type,
    ADD CONSTRAINT chk_idempotency_business_type
        CHECK (business_type IN ('TRANSFER', 'DEFERRED_TRANSFER'));

CREATE TABLE fund_movement_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    movement_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    available_before DECIMAL(19, 2) NOT NULL,
    available_after DECIMAL(19, 2) NOT NULL,
    frozen_before DECIMAL(19, 2) NOT NULL,
    frozen_after DECIMAL(19, 2) NOT NULL,
    total_before DECIMAL(19, 2) NOT NULL,
    total_after DECIMAL(19, 2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_fund_movement_no UNIQUE (movement_no),
    CONSTRAINT uk_fund_movement_business_action
        UNIQUE (business_type, business_id, account_id, action),
    INDEX idx_fund_movement_user_created (user_id, created_at, id),
    CONSTRAINT fk_fund_movement_account
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_fund_movement_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_fund_movement_business_type
        CHECK (business_type = 'DEFERRED_TRANSFER'),
    CONSTRAINT chk_fund_movement_action
        CHECK (action IN ('FREEZE', 'SETTLEMENT', 'UNFREEZE')),
    CONSTRAINT chk_fund_movement_amount_positive
        CHECK (amount > 0.00),
    CONSTRAINT chk_fund_movement_balances_non_negative
        CHECK (
            available_before >= 0.00 AND available_after >= 0.00
            AND frozen_before >= 0.00 AND frozen_after >= 0.00
            AND total_before >= 0.00 AND total_after >= 0.00
        ),
    CONSTRAINT chk_fund_movement_transition
        CHECK (
            (
                action = 'FREEZE'
                AND available_after = available_before - amount
                AND frozen_after = frozen_before + amount
                AND total_after = total_before
            )
            OR (
                action = 'SETTLEMENT'
                AND available_after = available_before
                AND frozen_after = frozen_before - amount
                AND total_after = total_before - amount
            )
            OR (
                action = 'UNFREEZE'
                AND available_after = available_before + amount
                AND frozen_after = frozen_before - amount
                AND total_after = total_before
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Auditable available and frozen balance movements';

CREATE TABLE risk_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL,
    business_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    rule_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    risk_level VARCHAR(10) NOT NULL,
    decision VARCHAR(10) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_risk_event_business_rule UNIQUE (business_no, rule_code),
    INDEX idx_risk_event_user_created (user_id, created_at, id),
    INDEX idx_risk_event_business_no (business_no, id),
    CONSTRAINT fk_risk_event_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_risk_event_transfer
        FOREIGN KEY (business_id) REFERENCES transfer_order (id),
    CONSTRAINT chk_risk_event_level
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_risk_event_decision
        CHECK (decision IN ('REVIEW', 'REJECT'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Persisted and explainable risk-rule decisions';
