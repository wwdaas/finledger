SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+00:00';

ALTER TABLE account
    DROP CHECK chk_account_balance_components,
    DROP CHECK chk_account_balance_non_negative,
    DROP COLUMN balance,
    ADD CONSTRAINT chk_account_total_balance_range
        CHECK (available_balance + frozen_balance <= 99999999999999999.99);
