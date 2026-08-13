package com.finledger.integration;

import com.finledger.account.dto.AccountResponse;
import com.finledger.account.exception.AccountAccessDeniedException;
import com.finledger.account.exception.AccountNotFoundException;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.ai.dto.AiAnalysisResponse;
import com.finledger.ai.service.AiTransactionAssistantService;
import com.finledger.common.money.InvalidAmountException;
import com.finledger.idempotency.mapper.IdempotencyRecordMapper;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import com.finledger.recharge.service.RechargeService;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.dto.TransferResponse;
import com.finledger.transfer.exception.InsufficientBalanceException;
import com.finledger.transfer.mapper.TransferOrderMapper;
import com.finledger.transfer.service.IdempotentTransferService;
import com.finledger.user.entity.UserEntity;
import com.finledger.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class FinancialFlowIntegrationTest {

    private static final String FAILURE_TRIGGER = "fail_transfer_record_insert";

    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private UserMapper userMapper;
    @Autowired private AccountMapper accountMapper;
    @Autowired private TransferOrderMapper transferOrderMapper;
    @Autowired private TransactionRecordMapper transactionRecordMapper;
    @Autowired private IdempotencyRecordMapper idempotencyRecordMapper;
    @Autowired private AccountService accountService;
    @Autowired private RechargeService rechargeService;
    @Autowired private IdempotentTransferService transferService;
    @Autowired private AiTransactionAssistantService aiAssistantService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
        dropFailureTrigger();
        jdbcTemplate.update("DELETE FROM transaction_record");
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM transfer_order");
        jdbcTemplate.update("DELETE FROM account");
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    @AfterEach
    void removeFailureTrigger() {
        dropFailureTrigger();
    }

    @Test
    void shouldTransferAtomicallyAndWriteTwoJournalEntries() {
        Long owner = createUser("normal_owner");
        Long receiver = createUser("normal_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("100.00"));

        TransferResponse response = transferService.transfer(
                owner, "normal-key", new TransferRequest(from, to, money("40.00"))
        );

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(balance(from)).isEqualByComparingTo("60.00");
        assertThat(balance(to)).isEqualByComparingTo("40.00");
        assertThat(transferOrderMapper.selectCount(null)).isEqualTo(1);
        List<TransactionRecordEntity> transferRecords = transactionRecordMapper.selectList(null)
                .stream().filter(record -> "TRANSFER".equals(record.getBusinessType())).toList();
        assertThat(transferRecords).extracting(TransactionRecordEntity::getDirection)
                .containsExactlyInAnyOrder("DEBIT", "CREDIT");
    }

    @Test
    void shouldRollBackInsufficientBalance() {
        Long owner = createUser("poor_owner");
        Long receiver = createUser("poor_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("20.00"));

        assertThatThrownBy(() -> transferService.transfer(
                owner, "insufficient-key", new TransferRequest(from, to, money("30.00"))))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(balance(from)).isEqualByComparingTo("20.00");
        assertThat(balance(to)).isEqualByComparingTo("0.00");
        assertThat(transferOrderMapper.selectCount(null)).isZero();
        assertThat(idempotencyRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void shouldRejectInvalidAmountBeforeDatabaseMutation() {
        Long owner = createUser("invalid_owner");
        Long receiver = createUser("invalid_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);

        assertThatThrownBy(() -> transferService.transfer(
                owner, "invalid-key", new TransferRequest(from, to, money("-1.00"))))
                .isInstanceOf(InvalidAmountException.class);

        assertThat(transferOrderMapper.selectCount(null)).isZero();
        assertThat(idempotencyRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void shouldRollBackClaimWhenAccountDoesNotExist() {
        Long owner = createUser("missing_owner");
        Long receiver = createUser("missing_receiver");
        Long to = createAccount(receiver);

        assertThatThrownBy(() -> transferService.transfer(
                owner, "missing-key", new TransferRequest(999999L, to, money("1.00"))))
                .isInstanceOf(AccountNotFoundException.class);

        assertThat(idempotencyRecordMapper.selectCount(null)).isZero();
        assertThat(transferOrderMapper.selectCount(null)).isZero();
    }

    @Test
    void shouldRollBackBalancesOrderAndClaimWhenLedgerInsertFails() {
        Long owner = createUser("rollback_owner");
        Long receiver = createUser("rollback_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("100.00"));
        createFailureTrigger();

        assertThatThrownBy(() -> transferService.transfer(
                owner, "rollback-key", new TransferRequest(from, to, money("25.00"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(balance(from)).isEqualByComparingTo("100.00");
        assertThat(balance(to)).isEqualByComparingTo("0.00");
        assertThat(transferOrderMapper.selectCount(null)).isZero();
        assertThat(idempotencyRecordMapper.selectCount(null)).isZero();
        assertThat(transactionRecordMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void shouldPreventOverspendingDuringConcurrentTransfers() throws Exception {
        Long owner = createUser("concurrent_owner");
        Long userB = createUser("concurrent_b");
        Long userC = createUser("concurrent_c");
        Long from = createAccount(owner);
        Long toB = createAccount(userB);
        Long toC = createAccount(userC);
        rechargeService.recharge(owner, from, money("100.00"));

        List<Attempt> attempts = runConcurrently(
                () -> attempt(owner, "concurrent-b", from, toB, "80.00"),
                () -> attempt(owner, "concurrent-c", from, toC, "80.00")
        );

        assertThat(attempts).filteredOn(Attempt::successful).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> !attempt.successful())
                .extracting(Attempt::failure)
                .allMatch(InsufficientBalanceException.class::isInstance);
        assertThat(balance(from)).isEqualByComparingTo("20.00");
        assertThat(balance(toB).add(balance(toC))).isEqualByComparingTo("80.00");
        assertThat(transferOrderMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void shouldExecuteConcurrentDuplicateIdempotencyKeyOnlyOnce() throws Exception {
        Long owner = createUser("idem_owner");
        Long receiver = createUser("idem_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("100.00"));

        List<Attempt> attempts = runConcurrently(
                () -> attempt(owner, "same-key", from, to, "30.00"),
                () -> attempt(owner, "same-key", from, to, "30.00")
        );

        assertThat(attempts).allMatch(Attempt::successful);
        assertThat(attempts.get(0).response().transferId())
                .isEqualTo(attempts.get(1).response().transferId());
        assertThat(balance(from)).isEqualByComparingTo("70.00");
        assertThat(balance(to)).isEqualByComparingTo("30.00");
        assertThat(transferOrderMapper.selectCount(null)).isEqualTo(1);
        assertThat(idempotencyRecordMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void shouldEnforceAuthenticationAndAccountOwnership() throws Exception {
        Long owner = createUser("permission_owner");
        Long stranger = createUser("permission_stranger");
        Long accountId = createAccount(owner);

        mockMvc.perform(get("/api/accounts/{id}", accountId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/accounts/{id}", accountId)
                        .with(jwt().jwt(token -> token.subject(stranger.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));

        assertThatThrownBy(() -> accountService.getOwnedAccount(stranger, accountId))
                .isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void shouldAnalyzeOnlyTheAuthenticatedUsersTransactions() {
        Long owner = createUser("ai_owner");
        Long receiver = createUser("ai_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("100.00"));
        transferService.transfer(
                owner, "ai-transfer-key", new TransferRequest(from, to, money("40.00"))
        );

        AiAnalysisResponse ownerResult = aiAssistantService.ask(owner, "我这个月转出去多少钱？");
        AiAnalysisResponse receiverResult = aiAssistantService.ask(receiver, "我这个月转出去多少钱？");

        assertThat(ownerResult.totalAmount()).isEqualByComparingTo("40.00");
        assertThat(ownerResult.transactionCount()).isEqualTo(1);
        assertThat(receiverResult.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(receiverResult.transactionCount()).isZero();
    }

    private static MySQLContainer mysqlContainer() {
        MySQLContainer container = new MySQLContainer("mysql:8.4.11");
        container.withDatabaseName("finledger_test");
        container.withUsername("finledger_test");
        container.withPassword("finledger_test_password");
        container.withInitScript("database/schema/V1__create_core_tables.sql");
        container.withCommand("--log-bin-trust-function-creators=1");
        container.withStartupTimeout(Duration.ofMinutes(2));
        return container;
    }

    private Long createUser(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash("integration-test-password-hash");
        user.setStatus("ACTIVE");
        assertThat(userMapper.insert(user)).isEqualTo(1);
        return user.getId();
    }

    private Long createAccount(Long userId) {
        AccountResponse account = accountService.create(userId);
        return account.id();
    }

    private BigDecimal balance(Long accountId) {
        return accountMapper.selectById(accountId).getBalance();
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private Attempt attempt(
            Long owner,
            String key,
            Long from,
            Long to,
            String amount
    ) {
        try {
            TransferResponse response = transferService.transfer(
                    owner, key, new TransferRequest(from, to, money(amount))
            );
            return new Attempt(response, null);
        } catch (RuntimeException exception) {
            return new Attempt(null, exception);
        }
    }

    private List<Attempt> runConcurrently(
            Callable<Attempt> first,
            Callable<Attempt> second
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Attempt> firstFuture = executor.submit(awaitStart(first, ready, start));
            Future<Attempt> secondFuture = executor.submit(awaitStart(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    firstFuture.get(20, TimeUnit.SECONDS),
                    secondFuture.get(20, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Callable<Attempt> awaitStart(
            Callable<Attempt> task,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start in time");
            }
            return task.call();
        };
    }

    private void createFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_transfer_record_insert
                BEFORE INSERT ON transaction_record
                FOR EACH ROW
                BEGIN
                    IF NEW.business_type = 'TRANSFER' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced ledger failure';
                    END IF;
                END
                """);
    }

    private void dropFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + FAILURE_TRIGGER);
    }

    private record Attempt(TransferResponse response, RuntimeException failure) {
        boolean successful() {
            return response != null;
        }
    }
}
