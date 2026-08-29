package com.finledger.integration;

import com.finledger.account.dto.AccountResponse;
import com.finledger.account.exception.AccountAccessDeniedException;
import com.finledger.account.exception.AccountNotFoundException;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.ai.dto.AiAnalysisResponse;
import com.finledger.ai.service.AiRiskExplanationService;
import com.finledger.ai.service.AiTransactionAssistantService;
import com.finledger.common.money.InvalidAmountException;
import com.finledger.freeze.dto.FreezeRequest;
import com.finledger.freeze.dto.FreezeResponse;
import com.finledger.freeze.entity.FundFreezeEntity;
import com.finledger.freeze.mapper.FundFreezeMapper;
import com.finledger.freeze.service.FreezeService;
import com.finledger.idempotency.exception.IdempotencyConflictException;
import com.finledger.idempotency.mapper.IdempotencyRecordMapper;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import com.finledger.recharge.service.RechargeService;
import com.finledger.risk.exception.RiskRejectedException;
import com.finledger.risk.mapper.RiskEventMapper;
import com.finledger.risk.service.RiskEventQueryService;
import com.finledger.account.exception.InsufficientAvailableBalanceException;
import com.finledger.settlement.mapper.FundMovementRecordMapper;
import com.finledger.settlement.service.PendingTransferService;
import com.finledger.settlement.service.SettlementService;
import com.finledger.settlement.service.CancellationService;
import com.finledger.settlement.service.DeferredTransferQueryService;
import com.finledger.settlement.exception.InvalidTransactionStateException;
import com.finledger.settlement.exception.TransactionNotFoundException;
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
import org.springframework.dao.DataAccessException;
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
    private static final String FREEZE_FAILURE_TRIGGER = "fail_fund_freeze_insert";
    private static final String UNFREEZE_FAILURE_TRIGGER = "fail_unfreeze_movement_insert";

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
    @Autowired private FundMovementRecordMapper fundMovementRecordMapper;
    @Autowired private FundFreezeMapper fundFreezeMapper;
    @Autowired private RiskEventMapper riskEventMapper;
    @Autowired private IdempotencyRecordMapper idempotencyRecordMapper;
    @Autowired private AccountService accountService;
    @Autowired private RechargeService rechargeService;
    @Autowired private IdempotentTransferService transferService;
    @Autowired private FreezeService freezeService;
    @Autowired private PendingTransferService pendingTransferService;
    @Autowired private SettlementService settlementService;
    @Autowired private CancellationService cancellationService;
    @Autowired private DeferredTransferQueryService deferredTransferQueryService;
    @Autowired private RiskEventQueryService riskEventQueryService;
    @Autowired private AiTransactionAssistantService aiAssistantService;
    @Autowired private AiRiskExplanationService aiRiskExplanationService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
        dropFailureTriggers();
        jdbcTemplate.update("DELETE FROM risk_event");
        jdbcTemplate.update("DELETE FROM fund_movement_record");
        jdbcTemplate.update("DELETE FROM transaction_record");
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM fund_freeze");
        jdbcTemplate.update("DELETE FROM transfer_order");
        jdbcTemplate.update("DELETE FROM account");
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    @AfterEach
    void removeFailureTrigger() {
        dropFailureTriggers();
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

    @Test
    void shouldFreezeAvailableFundsAtomically() {
        Long owner = createUser("freeze_owner");
        Long receiver = createUser("freeze_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1000.00"));

        var response = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("300.00"))
        );

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(balance(from)).isEqualByComparingTo("1000.00");
        assertThat(availableBalance(from)).isEqualByComparingTo("700.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("300.00");
        assertThat(fundMovementRecordMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void shouldRollBackInsufficientAvailableBalanceFreeze() {
        Long owner = createUser("freeze_poor_owner");
        Long receiver = createUser("freeze_poor_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("100.00"));

        assertThatThrownBy(() -> pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("300.00"))
        )).isInstanceOf(InsufficientAvailableBalanceException.class);

        assertThat(balance(from)).isEqualByComparingTo("100.00");
        assertThat(availableBalance(from)).isEqualByComparingTo("100.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("0.00");
        assertThat(transferOrderMapper.selectCount(null)).isZero();
        assertThat(fundMovementRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void shouldSettleReservedFundsAndCreditDestination() {
        Long owner = createUser("settle_owner");
        Long receiver = createUser("settle_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1000.00"));
        var pending = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("300.00"))
        );

        var settled = settlementService.settle(owner, pending.transferId());

        assertThat(settled.status()).isEqualTo("SETTLED");
        assertThat(balance(from)).isEqualByComparingTo("700.00");
        assertThat(availableBalance(from)).isEqualByComparingTo("700.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("0.00");
        assertThat(balance(to)).isEqualByComparingTo("300.00");
        assertThat(availableBalance(to)).isEqualByComparingTo("300.00");
        assertThat(transactionRecordMapper.selectList(null).stream()
                .filter(record -> "TRANSFER".equals(record.getBusinessType())))
                .extracting(TransactionRecordEntity::getDirection)
                .containsExactlyInAnyOrder("DEBIT", "CREDIT");
        assertThat(fundMovementRecordMapper.selectCount(null)).isEqualTo(2);
    }

    @Test
    void shouldCancelAndUnfreezeReservedFundsOnlyOnce() {
        Long owner = createUser("cancel_owner");
        Long receiver = createUser("cancel_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1000.00"));
        var pending = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("300.00"))
        );

        var cancelled = cancellationService.cancel(owner, pending.transferId());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(balance(from)).isEqualByComparingTo("1000.00");
        assertThat(availableBalance(from)).isEqualByComparingTo("1000.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("0.00");
        assertThat(balance(to)).isEqualByComparingTo("0.00");
        assertThat(transactionRecordMapper.selectList(null).stream()
                .filter(record -> "TRANSFER".equals(record.getBusinessType())))
                .isEmpty();
        assertThat(fundMovementRecordMapper.selectCount(null)).isEqualTo(2);

        assertThatThrownBy(() -> cancellationService.cancel(owner, pending.transferId()))
                .isInstanceOf(InvalidTransactionStateException.class);
        assertThat(availableBalance(from)).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldRejectRepeatedSettlementWithoutDoubleDebit() {
        Long owner = createUser("repeat_settle_owner");
        Long receiver = createUser("repeat_settle_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1000.00"));
        var pending = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("300.00"))
        );
        settlementService.settle(owner, pending.transferId());

        assertThatThrownBy(() -> settlementService.settle(owner, pending.transferId()))
                .isInstanceOf(InvalidTransactionStateException.class);

        assertThat(balance(from)).isEqualByComparingTo("700.00");
        assertThat(availableBalance(from)).isEqualByComparingTo("700.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("0.00");
        assertThat(balance(to)).isEqualByComparingTo("300.00");
        assertThat(fundMovementRecordMapper.selectCount(null)).isEqualTo(2);
    }

    @Test
    void shouldRollBackSettlementWhenLedgerWriteFails() {
        Long owner = createUser("settle_rollback_owner");
        Long receiver = createUser("settle_rollback_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1000.00"));
        var pending = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("300.00"))
        );
        createFailureTrigger();

        assertThatThrownBy(() -> settlementService.settle(owner, pending.transferId()))
                .isInstanceOf(RuntimeException.class);

        assertThat(transferOrderMapper.selectById(pending.transferId()).getStatus()).isEqualTo("PENDING");
        assertThat(availableBalance(from)).isEqualByComparingTo("700.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("300.00");
        assertThat(balance(to)).isEqualByComparingTo("0.00");
        assertThat(transactionRecordMapper.selectList(null).stream()
                .filter(record -> "TRANSFER".equals(record.getBusinessType())))
                .isEmpty();
        assertThat(fundMovementRecordMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void shouldRollBackCancellationWhenUnfreezeMovementWriteFails() {
        Long owner = createUser("cancel_rollback_owner");
        Long receiver = createUser("cancel_rollback_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1000.00"));
        var pending = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("300.00"))
        );
        createUnfreezeFailureTrigger();

        assertThatThrownBy(() -> cancellationService.cancel(owner, pending.transferId()))
                .isInstanceOf(RuntimeException.class);

        assertThat(transferOrderMapper.selectById(pending.transferId()).getStatus()).isEqualTo("PENDING");
        assertThat(availableBalance(from)).isEqualByComparingTo("700.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("300.00");
        assertThat(balance(to)).isEqualByComparingTo("0.00");
        assertThat(fundMovementRecordMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void shouldHideDeferredTransferAndLifecycleActionsFromOtherUsers() {
        Long owner = createUser("lifecycle_permission_owner");
        Long stranger = createUser("lifecycle_permission_stranger");
        Long receiver = createUser("lifecycle_permission_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1000.00"));
        var pending = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("300.00"))
        );

        assertThatThrownBy(() -> deferredTransferQueryService.getOwnedById(stranger, pending.transferId()))
                .isInstanceOf(TransactionNotFoundException.class);
        assertThatThrownBy(() -> settlementService.settle(stranger, pending.transferId()))
                .isInstanceOf(TransactionNotFoundException.class);
        assertThatThrownBy(() -> cancellationService.cancel(stranger, pending.transferId()))
                .isInstanceOf(TransactionNotFoundException.class);

        assertThat(transferOrderMapper.selectById(pending.transferId()).getStatus()).isEqualTo("PENDING");
        assertThat(availableBalance(from)).isEqualByComparingTo("700.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("300.00");
        assertThat(balance(to)).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldAllowOnlyOneOfConcurrentSettlementAndCancellation() throws Exception {
        Long owner = createUser("state_race_owner");
        Long receiver = createUser("state_race_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1000.00"));
        var pending = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("300.00"))
        );

        List<LifecycleAttempt> attempts = runConcurrently(
                () -> lifecycleAttempt(() -> settlementService.settle(owner, pending.transferId())),
                () -> lifecycleAttempt(() -> cancellationService.cancel(owner, pending.transferId()))
        );

        assertThat(attempts).filteredOn(LifecycleAttempt::successful).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> !attempt.successful())
                .extracting(LifecycleAttempt::failure)
                .allMatch(InvalidTransactionStateException.class::isInstance);
        String finalStatus = transferOrderMapper.selectById(pending.transferId()).getStatus();
        assertThat(finalStatus).isIn("SETTLED", "CANCELLED");
        assertThat(frozenBalance(from)).isEqualByComparingTo("0.00");
        assertThat(balance(from).add(balance(to))).isEqualByComparingTo("1000.00");
        if ("SETTLED".equals(finalStatus)) {
            assertThat(balance(from)).isEqualByComparingTo("700.00");
            assertThat(balance(to)).isEqualByComparingTo("300.00");
        } else {
            assertThat(balance(from)).isEqualByComparingTo("1000.00");
            assertThat(balance(to)).isEqualByComparingTo("0.00");
        }
        assertThat(fundMovementRecordMapper.selectCount(null)).isEqualTo(2);
    }

    @Test
    void shouldPersistHighAmountReviewAndAllowPendingTransfer() {
        Long owner = createUser("risk_high_owner");
        Long receiver = createUser("risk_high_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("60000.00"));

        var pending = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("50000.01"))
        );

        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.riskDecision()).isEqualTo("REVIEW");
        assertThat(riskEventQueryService.findByBusinessNo(owner, pending.transferNo()))
                .extracting(event -> event.ruleCode())
                .containsExactly("HIGH_AMOUNT");
        assertThat(riskEventQueryService.findByBusinessNo(receiver, pending.transferNo()))
                .isEmpty();
    }

    @Test
    void shouldPersistDailyLimitRejectionWithoutFreezingMoreFunds() {
        Long owner = createUser("risk_daily_owner");
        Long receiver = createUser("risk_daily_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("250000.00"));
        pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("190000.00"))
        );

        RiskRejectedException rejected = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> pendingTransferService.createPending(
                        owner, new TransferRequest(from, to, money("20000.01"))
                ),
                RiskRejectedException.class
        );

        assertThat(rejected).isNotNull();
        assertThat(balance(from)).isEqualByComparingTo("250000.00");
        assertThat(availableBalance(from)).isEqualByComparingTo("60000.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("190000.00");
        var rejectedOrder = transferOrderMapper.selectList(null).stream()
                .filter(order -> rejected.getTransactionNo().equals(order.getTransferNo()))
                .findFirst().orElseThrow();
        assertThat(rejectedOrder.getStatus()).isEqualTo("FAILED");
        assertThat(rejectedOrder.getRiskDecision()).isEqualTo("REJECT");
        assertThat(riskEventQueryService.findByBusinessNo(owner, rejected.getTransactionNo()))
                .extracting(event -> event.ruleCode())
                .containsExactly("DAILY_LIMIT");
        assertThat(riskEventMapper.selectCount(null)).isEqualTo(2);
    }

    @Test
    void shouldExplainOnlyTheAuthenticatedUsersDeferredTransaction() {
        Long owner = createUser("ai_risk_owner");
        Long receiver = createUser("ai_risk_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("60000.00"));
        var pending = pendingTransferService.createPending(
                owner, new TransferRequest(from, to, money("50000.01"))
        );
        String question = "交易 " + pending.transferNo() + " 为什么触发风控？";

        var explanation = aiRiskExplanationService.explain(owner, question);

        assertThat(explanation.transactionNo()).isEqualTo(pending.transferNo());
        assertThat(explanation.status()).isEqualTo("PENDING");
        assertThat(explanation.riskDecision()).isEqualTo("REVIEW");
        assertThat(explanation.riskEvents()).extracting(event -> event.ruleCode())
                .containsExactly("HIGH_AMOUNT");
        assertThatThrownBy(() -> aiRiskExplanationService.explain(receiver, question))
                .isInstanceOf(com.finledger.settlement.exception.TransactionNotFoundException.class);
    }

    @Test
    void shouldRechargeAvailableBalanceWithoutChangingFrozenBalance() {
        Long owner = createUser("dual_recharge_owner");
        Long accountId = createAccount(owner);
        rechargeService.recharge(owner, accountId, money("150.00"));
        setBalanceComponents(accountId, "100.00", "50.00");

        var response = rechargeService.recharge(owner, accountId, money("200.00"));

        assertThat(response.availableBalance()).isEqualByComparingTo("300.00");
        assertThat(response.frozenBalance()).isEqualByComparingTo("50.00");
        assertThat(response.totalBalance()).isEqualByComparingTo("350.00");
        assertThat(availableBalance(accountId)).isEqualByComparingTo("300.00");
        assertThat(frozenBalance(accountId)).isEqualByComparingTo("50.00");
        assertThat(balance(accountId)).isEqualByComparingTo("350.00");
        TransactionRecordEntity latest = transactionRecordMapper.selectList(null).stream()
                .max(java.util.Comparator.comparing(TransactionRecordEntity::getId))
                .orElseThrow();
        assertThat(latest.getBalanceBefore()).isEqualByComparingTo("150.00");
        assertThat(latest.getBalanceAfter()).isEqualByComparingTo("350.00");
    }

    @Test
    void shouldTransferAvailableBalanceAndPreserveFrozenBalance() {
        Long owner = createUser("dual_transfer_owner");
        Long receiver = createUser("dual_transfer_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1200.00"));
        rechargeService.recharge(receiver, to, money("500.00"));
        setBalanceComponents(from, "1000.00", "200.00");

        TransferResponse response = transferService.transfer(
                owner, "dual-transfer-key", new TransferRequest(from, to, money("300.00"))
        );

        assertThat(response.fromTotalBalance()).isEqualByComparingTo("900.00");
        assertThat(response.toTotalBalance()).isEqualByComparingTo("800.00");
        assertThat(availableBalance(from)).isEqualByComparingTo("700.00");
        assertThat(availableBalance(to)).isEqualByComparingTo("800.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("200.00");
        assertThat(balance(from)).isEqualByComparingTo("900.00");
    }

    @Test
    void shouldNotSpendFrozenBalanceInImmediateTransfer() {
        Long owner = createUser("frozen_spend_owner");
        Long receiver = createUser("frozen_spend_receiver");
        Long from = createAccount(owner);
        Long to = createAccount(receiver);
        rechargeService.recharge(owner, from, money("1000.00"));
        setBalanceComponents(from, "100.00", "900.00");

        assertThatThrownBy(() -> transferService.transfer(
                owner, "frozen-spend-key", new TransferRequest(from, to, money("500.00"))
        )).isInstanceOf(InsufficientBalanceException.class);

        assertThat(availableBalance(from)).isEqualByComparingTo("100.00");
        assertThat(frozenBalance(from)).isEqualByComparingTo("900.00");
        assertThat(balance(from)).isEqualByComparingTo("1000.00");
        assertThat(balance(to)).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldEnforceDualBalanceDatabaseConstraints() {
        Long owner = createUser("dual_constraint_owner");
        Long accountId = createAccount(owner);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE account SET available_balance = -0.01 WHERE id = ?", accountId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE account SET frozen_balance = -0.01 WHERE id = ?", accountId
        )).isInstanceOf(DataAccessException.class);

        assertThat(availableBalance(accountId)).isEqualByComparingTo("0.00");
        assertThat(frozenBalance(accountId)).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldFreezeFundsAndPreserveTotalBalance() {
        Long owner = createUser("standalone_freeze_owner");
        Long accountId = createAccount(owner);
        rechargeService.recharge(owner, accountId, money("1000.00"));

        FreezeResponse response = freezeService.freeze(
                owner,
                accountId,
                "freeze-normal-key",
                new FreezeRequest(money("300.00"), "TRADE", "Pending transaction")
        );

        assertThat(response.status()).isEqualTo("FROZEN");
        assertThat(response.freezeNo()).startsWith("FRZ");
        assertThat(response.availableBalance()).isEqualByComparingTo("700.00");
        assertThat(response.frozenBalance()).isEqualByComparingTo("300.00");
        assertThat(response.totalBalance()).isEqualByComparingTo("1000.00");
        assertThat(balance(accountId)).isEqualByComparingTo("1000.00");
        FundFreezeEntity stored = fundFreezeMapper.selectById(response.freezeId());
        assertThat(stored.getUserId()).isEqualTo(owner);
        assertThat(stored.getAccountId()).isEqualTo(accountId);
        assertThat(stored.getAmount()).isEqualByComparingTo("300.00");
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(fundMovementRecordMapper.selectList(null))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.getBusinessType()).isEqualTo("FUND_FREEZE");
                    assertThat(movement.getAction()).isEqualTo("FREEZE");
                    assertThat(movement.getTotalBefore()).isEqualByComparingTo("1000.00");
                    assertThat(movement.getTotalAfter()).isEqualByComparingTo("1000.00");
                });
    }

    @Test
    void shouldRejectInvalidOrInsufficientStandaloneFreezeWithoutMutation() {
        Long owner = createUser("standalone_freeze_validation_owner");
        Long accountId = createAccount(owner);
        rechargeService.recharge(owner, accountId, money("100.00"));
        setBalanceComponents(accountId, "100.00", "900.00");

        assertThatThrownBy(() -> freezeService.freeze(
                owner, accountId, "freeze-zero", new FreezeRequest(money("0.00"), "TRADE", null)
        )).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> freezeService.freeze(
                owner, accountId, "freeze-negative", new FreezeRequest(money("-100.00"), "TRADE", null)
        )).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> freezeService.freeze(
                owner, accountId, "freeze-insufficient", new FreezeRequest(money("300.00"), "TRADE", null)
        )).isInstanceOf(InsufficientAvailableBalanceException.class);

        assertThat(availableBalance(accountId)).isEqualByComparingTo("100.00");
        assertThat(frozenBalance(accountId)).isEqualByComparingTo("900.00");
        assertThat(fundFreezeMapper.selectCount(null)).isZero();
        assertThat(idempotencyRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void shouldRejectMissingAndUnownedAccountForStandaloneFreeze() {
        Long owner = createUser("standalone_freeze_permission_owner");
        Long stranger = createUser("standalone_freeze_permission_stranger");
        Long accountId = createAccount(owner);
        rechargeService.recharge(owner, accountId, money("100.00"));
        FreezeRequest request = new FreezeRequest(money("20.00"), "TRADE", null);

        assertThatThrownBy(() -> freezeService.freeze(
                owner, 999999L, "freeze-missing", request
        )).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> freezeService.freeze(
                stranger, accountId, "freeze-unowned", request
        )).isInstanceOf(AccountAccessDeniedException.class);

        assertThat(availableBalance(accountId)).isEqualByComparingTo("100.00");
        assertThat(frozenBalance(accountId)).isEqualByComparingTo("0.00");
        assertThat(fundFreezeMapper.selectCount(null)).isZero();
        assertThat(idempotencyRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void shouldAllowOnlyOneConcurrentFreezeWhenAvailableBalanceIsInsufficientForBoth() throws Exception {
        Long owner = createUser("standalone_freeze_concurrent_owner");
        Long accountId = createAccount(owner);
        rechargeService.recharge(owner, accountId, money("100.00"));

        List<FreezeAttempt> attempts = runConcurrently(
                () -> freezeAttempt(owner, accountId, "freeze-concurrent-a", "80.00"),
                () -> freezeAttempt(owner, accountId, "freeze-concurrent-b", "80.00")
        );

        assertThat(attempts).filteredOn(FreezeAttempt::successful).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> !attempt.successful())
                .extracting(FreezeAttempt::failure)
                .allMatch(InsufficientAvailableBalanceException.class::isInstance);
        assertThat(availableBalance(accountId)).isEqualByComparingTo("20.00");
        assertThat(frozenBalance(accountId)).isEqualByComparingTo("80.00");
        assertThat(balance(accountId)).isEqualByComparingTo("100.00");
        assertThat(fundFreezeMapper.selectCount(null)).isEqualTo(1);
        assertThat(fundMovementRecordMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void shouldRollBackEntireFreezeWhenFreezeRecordInsertFails() {
        Long owner = createUser("standalone_freeze_rollback_owner");
        Long accountId = createAccount(owner);
        rechargeService.recharge(owner, accountId, money("1000.00"));
        createFreezeFailureTrigger();

        assertThatThrownBy(() -> freezeService.freeze(
                owner,
                accountId,
                "freeze-rollback-key",
                new FreezeRequest(money("300.00"), "TRADE", null)
        )).isInstanceOf(RuntimeException.class);

        assertThat(availableBalance(accountId)).isEqualByComparingTo("1000.00");
        assertThat(frozenBalance(accountId)).isEqualByComparingTo("0.00");
        assertThat(fundFreezeMapper.selectCount(null)).isZero();
        assertThat(fundMovementRecordMapper.selectCount(null)).isZero();
        assertThat(idempotencyRecordMapper.selectCount(null)).isZero();
    }

    @Test
    void shouldReplaySequentialStandaloneFreezeOnlyOnceAndRejectChangedRequest() {
        Long owner = createUser("standalone_freeze_idem_owner");
        Long accountId = createAccount(owner);
        rechargeService.recharge(owner, accountId, money("1000.00"));
        FreezeRequest request = new FreezeRequest(money("300.00"), "TRADE", "Same request");

        FreezeResponse first = freezeService.freeze(owner, accountId, "freeze-idem-key", request);
        FreezeResponse replay = freezeService.freeze(owner, accountId, "freeze-idem-key", request);

        assertThat(replay.freezeId()).isEqualTo(first.freezeId());
        assertThat(replay.freezeNo()).isEqualTo(first.freezeNo());
        assertThat(replay.amount()).isEqualByComparingTo(first.amount());
        assertThat(replay.availableBalance()).isEqualByComparingTo(first.availableBalance());
        assertThat(replay.frozenBalance()).isEqualByComparingTo(first.frozenBalance());
        assertThat(availableBalance(accountId)).isEqualByComparingTo("700.00");
        assertThat(frozenBalance(accountId)).isEqualByComparingTo("300.00");
        assertThat(fundFreezeMapper.selectCount(null)).isEqualTo(1);
        assertThat(idempotencyRecordMapper.selectCount(null)).isEqualTo(1);
        assertThatThrownBy(() -> freezeService.freeze(
                owner,
                accountId,
                "freeze-idem-key",
                new FreezeRequest(money("301.00"), "TRADE", "Same request")
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void shouldExecuteConcurrentDuplicateStandaloneFreezeOnlyOnce() throws Exception {
        Long owner = createUser("standalone_freeze_concurrent_idem_owner");
        Long accountId = createAccount(owner);
        rechargeService.recharge(owner, accountId, money("1000.00"));

        List<FreezeAttempt> attempts = runConcurrently(
                () -> freezeAttempt(owner, accountId, "freeze-same-key", "300.00"),
                () -> freezeAttempt(owner, accountId, "freeze-same-key", "300.00")
        );

        assertThat(attempts).allMatch(FreezeAttempt::successful);
        assertThat(attempts.get(0).response().freezeId())
                .isEqualTo(attempts.get(1).response().freezeId());
        assertThat(availableBalance(accountId)).isEqualByComparingTo("700.00");
        assertThat(frozenBalance(accountId)).isEqualByComparingTo("300.00");
        assertThat(fundFreezeMapper.selectCount(null)).isEqualTo(1);
        assertThat(fundMovementRecordMapper.selectCount(null)).isEqualTo(1);
        assertThat(idempotencyRecordMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void shouldEnforceFundFreezeDatabaseConstraints() {
        Long owner = createUser("standalone_freeze_constraint_owner");
        Long accountId = createAccount(owner);
        String insert = """
                INSERT INTO fund_freeze
                    (freeze_no, user_id, account_id, amount, status, business_type, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        assertThat(jdbcTemplate.update(
                insert, "FRZ-CONSTRAINT-1", owner, accountId, money("1.00"), "FROZEN", "TRADE", "ok"
        )).isEqualTo(1);
        FundFreezeEntity stored = fundFreezeMapper.selectList(null).get(0);
        assertThat(stored.getUserId()).isEqualTo(owner);
        assertThat(stored.getAccountId()).isEqualTo(accountId);
        assertThat(stored.getCreatedAt()).isNotNull();

        assertThatThrownBy(() -> jdbcTemplate.update(
                insert, "FRZ-CONSTRAINT-1", owner, accountId, money("2.00"), "FROZEN", "TRADE", null
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                insert, "FRZ-CONSTRAINT-2", owner, accountId, money("0.00"), "FROZEN", "TRADE", null
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                insert, "FRZ-CONSTRAINT-3", owner, accountId, money("1.00"), "SETTLED", "TRADE", null
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                insert, "FRZ-CONSTRAINT-4", owner, accountId, money("1.00"), "FROZEN", "trade", null
        )).isInstanceOf(DataAccessException.class);
        assertThat(fundFreezeMapper.selectCount(null)).isEqualTo(1);
    }

    private static MySQLContainer mysqlContainer() {
        MySQLContainer container = new MySQLContainer("mysql:8.4.11");
        container.withDatabaseName("finledger_test");
        container.withUsername("finledger_test");
        container.withPassword("finledger_test_password");
        container.withInitScripts(
                "database/schema/V1__create_core_tables.sql",
                "database/schema/V2__add_settlement_and_risk.sql",
                "database/schema/V3__remove_redundant_account_balance.sql",
                "database/schema/V4__add_fund_freeze.sql"
        );
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
        return accountMapper.selectById(accountId).getTotalBalance();
    }

    private BigDecimal availableBalance(Long accountId) {
        return accountMapper.selectById(accountId).getAvailableBalance();
    }

    private BigDecimal frozenBalance(Long accountId) {
        return accountMapper.selectById(accountId).getFrozenBalance();
    }

    private void setBalanceComponents(Long accountId, String available, String frozen) {
        assertThat(jdbcTemplate.update(
                "UPDATE account SET available_balance = ?, frozen_balance = ? WHERE id = ?",
                money(available), money(frozen), accountId
        )).isEqualTo(1);
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

    private LifecycleAttempt lifecycleAttempt(Runnable action) {
        try {
            action.run();
            return new LifecycleAttempt(true, null);
        } catch (RuntimeException exception) {
            return new LifecycleAttempt(false, exception);
        }
    }

    private FreezeAttempt freezeAttempt(Long owner, Long accountId, String key, String amount) {
        try {
            FreezeResponse response = freezeService.freeze(
                    owner,
                    accountId,
                    key,
                    new FreezeRequest(money(amount), "TRADE", "Concurrent freeze")
            );
            return new FreezeAttempt(response, null);
        } catch (RuntimeException exception) {
            return new FreezeAttempt(null, exception);
        }
    }

    private <T> List<T> runConcurrently(
            Callable<T> first,
            Callable<T> second
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> firstFuture = executor.submit(awaitStart(first, ready, start));
            Future<T> secondFuture = executor.submit(awaitStart(second, ready, start));
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

    private <T> Callable<T> awaitStart(
            Callable<T> task,
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

    private void createFreezeFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_fund_freeze_insert
                BEFORE INSERT ON fund_freeze
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced freeze record failure'
                """);
    }

    private void createUnfreezeFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_unfreeze_movement_insert
                BEFORE INSERT ON fund_movement_record
                FOR EACH ROW
                BEGIN
                    IF NEW.action = 'UNFREEZE' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced unfreeze movement failure';
                    END IF;
                END
                """);
    }

    private void dropFailureTriggers() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + FAILURE_TRIGGER);
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + FREEZE_FAILURE_TRIGGER);
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + UNFREEZE_FAILURE_TRIGGER);
    }

    private record Attempt(TransferResponse response, RuntimeException failure) {
        boolean successful() {
            return response != null;
        }
    }

    private record LifecycleAttempt(boolean successful, RuntimeException failure) {
    }

    private record FreezeAttempt(FreezeResponse response, RuntimeException failure) {
        boolean successful() {
            return response != null;
        }
    }
}
