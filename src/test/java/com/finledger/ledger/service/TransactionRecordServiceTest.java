package com.finledger.ledger.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finledger.account.service.AccountService;
import com.finledger.common.api.PageResponse;
import com.finledger.ledger.dto.TransactionRecordResponse;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.exception.InvalidTransactionFilterException;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionRecordServiceTest {

    @Mock private TransactionRecordMapper transactionRecordMapper;
    @Mock private AccountService accountService;

    private TransactionRecordService service;

    @BeforeEach
    void setUp() {
        service = new TransactionRecordService(transactionRecordMapper, accountService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnPagedUserTransactions() {
        TransactionRecordEntity record = new TransactionRecordEntity();
        record.setId(1L);
        record.setUserId(10L);
        record.setAccountId(100L);
        record.setRecordNo("TR001");
        record.setBusinessType("TRANSFER");
        record.setBusinessId(20L);
        record.setDirection("DEBIT");
        record.setAmount(new BigDecimal("12.50"));
        record.setCurrency("CNY");
        record.setBalanceBefore(new BigDecimal("50.00"));
        record.setBalanceAfter(new BigDecimal("37.50"));

        when(transactionRecordMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<TransactionRecordEntity> page = invocation.getArgument(0);
                    page.setRecords(List.of(record));
                    page.setTotal(1);
                    return page;
                });

        PageResponse<TransactionRecordResponse> result = service.query(
                10L, null, "transfer", "debit", null, null, 1, 20
        );

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement()
                .extracting(TransactionRecordResponse::direction)
                .isEqualTo("DEBIT");
    }

    @Test
    void shouldRejectUnknownDirection() {
        assertThatThrownBy(() -> service.query(
                10L, null, null, "SIDEWAYS", null, null, 1, 20
        )).isInstanceOf(InvalidTransactionFilterException.class);
    }
}
