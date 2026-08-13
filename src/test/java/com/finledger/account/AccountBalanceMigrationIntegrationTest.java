package com.finledger.account;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AccountBalanceMigrationIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("finledger_migration_test")
            .withUsername("finledger_test")
            .withPassword("finledger_test_password")
            .withInitScript("database/schema/V1__create_core_tables.sql")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Test
    void shouldPreserveLegacyBalanceWhenMigratingToDualBalanceModel() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()
        )) {
            insertLegacyAccount(connection);

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("database/schema/V2__add_settlement_and_risk.sql")
            );
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("database/schema/V3__remove_redundant_account_balance.sql")
            );

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT available_balance, frozen_balance "
                            + "FROM account WHERE id = 10"
            ); ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                BigDecimal available = result.getBigDecimal("available_balance");
                BigDecimal frozen = result.getBigDecimal("frozen_balance");
                assertThat(available).isEqualByComparingTo("1000.00");
                assertThat(frozen).isEqualByComparingTo("0.00");
                assertThat(available.add(frozen)).isEqualByComparingTo("1000.00");
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'account'
                      AND COLUMN_NAME = 'balance'
                    """); ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
        }
    }

    private void insertLegacyAccount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO sys_user (id, username, password_hash, status)
                    VALUES (1, 'migration_user', 'migration-password-hash', 'ACTIVE')
                    """);
            statement.executeUpdate("""
                    INSERT INTO account (
                        id, user_id, account_no, balance, currency, status, version
                    ) VALUES (
                        10, 1, 'FLMIGRATION0000000001', 1000.00, 'CNY', 'ACTIVE', 0
                    )
                    """);
        }
    }
}
