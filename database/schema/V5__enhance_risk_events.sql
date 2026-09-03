SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+00:00';

ALTER TABLE risk_event
    ADD COLUMN amount DECIMAL(19, 2) NULL AFTER business_no,
    ADD COLUMN metadata_json JSON NULL AFTER reason;

UPDATE risk_event event
JOIN transfer_order transfer_order ON transfer_order.id = event.business_id
SET event.amount = transfer_order.amount
WHERE event.amount IS NULL;

ALTER TABLE risk_event
    MODIFY COLUMN amount DECIMAL(19, 2) NOT NULL,
    ADD CONSTRAINT chk_risk_event_amount_positive CHECK (amount > 0.00),
    ADD INDEX idx_risk_event_user_decision_created (user_id, decision, created_at, id),
    ADD INDEX idx_risk_event_user_rule_created (user_id, rule_code, created_at, id);
