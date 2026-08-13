package com.finledger.recharge.service;

import com.finledger.account.entity.AccountEntity;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.generator.TransactionRecordNumberGenerator;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import com.finledger.recharge.dto.RechargeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RechargeServiceTest {

    @Mock private AccountService accountService;
    @Mock private AccountMapper accountMapper;
    @Mock private TransactionRecordMapper transactionRecordMapper;
    @Mock private TransactionRecordNumberGenerator recordNumberGenerator;

    private RechargeService rechargeService;

    @BeforeEach
    void setUp() {
        rechargeService = new RechargeService(
                accountService, accountMapper, transactionRecordMapper, recordNumberGenerator
        );
    }

    @Test
    void shouldCreditAccountAndWriteJournal() {
        AccountEntity account = new AccountEntity();
        account.setId(10L);
        account.setUserId(1L);
        account.setBalance(new BigDecimal("100.00"));
        account.setCurrency("CNY");
        account.setStatus("ACTIVE");
        account.setVersion(0L);
        when(accountService.lockOwnedAccount(1L, 10L)).thenReturn(account);
        when(accountMapper.updateById(account)).thenReturn(1);
        when(recordNumberGenerator.nextRecordNo()).thenReturn("TR001");
        when(transactionRecordMapper.insert(any(TransactionRecordEntity.class))).thenReturn(1);

        RechargeResponse response = rechargeService.recharge(1L, 10L, new BigDecimal("25.00"));

        assertThat(response.balance()).isEqualByComparingTo("125.00");
        assertThat(account.getVersion()).isEqualTo(1L);
        ArgumentCaptor<TransactionRecordEntity> recordCaptor =
                ArgumentCaptor.forClass(TransactionRecordEntity.class);
        verify(transactionRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getDirection()).isEqualTo("CREDIT");
        assertThat(recordCaptor.getValue().getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(recordCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("125.00");
    }
}
