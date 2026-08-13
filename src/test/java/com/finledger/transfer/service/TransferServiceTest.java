package com.finledger.transfer.service;

import com.finledger.account.entity.AccountEntity;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.account.service.LockedTransferAccounts;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.generator.TransactionRecordNumberGenerator;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.dto.TransferResponse;
import com.finledger.transfer.entity.TransferOrderEntity;
import com.finledger.transfer.exception.InsufficientBalanceException;
import com.finledger.transfer.generator.TransferNumberGenerator;
import com.finledger.transfer.mapper.TransferOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private AccountService accountService;
    @Mock private AccountMapper accountMapper;
    @Mock private TransferOrderMapper transferOrderMapper;
    @Mock private TransactionRecordMapper transactionRecordMapper;
    @Mock private TransferNumberGenerator transferNumberGenerator;
    @Mock private TransactionRecordNumberGenerator recordNumberGenerator;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                accountService, accountMapper, transferOrderMapper, transactionRecordMapper,
                transferNumberGenerator, recordNumberGenerator
        );
    }

    @Test
    void shouldTransferAndWriteTwoJournalRecords() {
        AccountEntity from = account(1L, 10L, "100.00");
        AccountEntity to = account(2L, 20L, "20.00");
        when(accountService.lockTransferAccounts(10L, 1L, 2L))
                .thenReturn(new LockedTransferAccounts(from, to));
        when(accountMapper.updateById(any(AccountEntity.class))).thenReturn(1);
        when(transferNumberGenerator.nextTransferNo()).thenReturn("TF001");
        doAnswer(invocation -> {
            TransferOrderEntity order = invocation.getArgument(0);
            order.setId(99L);
            return 1;
        }).when(transferOrderMapper).insert(any(TransferOrderEntity.class));
        when(recordNumberGenerator.nextRecordNo()).thenReturn("TR001", "TR002");
        when(transactionRecordMapper.insert(any(TransactionRecordEntity.class))).thenReturn(1);

        TransferResponse response = transferService.transfer(
                10L, new TransferRequest(1L, 2L, new BigDecimal("80.00"))
        );

        assertThat(response.fromBalance()).isEqualByComparingTo("20.00");
        assertThat(response.toBalance()).isEqualByComparingTo("100.00");
        ArgumentCaptor<TransactionRecordEntity> records =
                ArgumentCaptor.forClass(TransactionRecordEntity.class);
        verify(transactionRecordMapper, org.mockito.Mockito.times(2)).insert(records.capture());
        assertThat(records.getAllValues())
                .extracting(TransactionRecordEntity::getDirection)
                .containsExactly("DEBIT", "CREDIT");
    }

    @Test
    void shouldRejectInsufficientBalanceBeforeUpdates() {
        AccountEntity from = account(1L, 10L, "50.00");
        AccountEntity to = account(2L, 20L, "0.00");
        when(accountService.lockTransferAccounts(10L, 1L, 2L))
                .thenReturn(new LockedTransferAccounts(from, to));

        assertThatThrownBy(() -> transferService.transfer(
                10L, new TransferRequest(1L, 2L, new BigDecimal("80.00"))
        )).isInstanceOf(InsufficientBalanceException.class);
    }

    private AccountEntity account(Long id, Long userId, String balance) {
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setUserId(userId);
        account.setBalance(new BigDecimal(balance));
        account.setCurrency("CNY");
        account.setStatus("ACTIVE");
        account.setVersion(0L);
        return account;
    }
}
