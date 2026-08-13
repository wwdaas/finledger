package com.finledger.ai.service;

import com.finledger.ai.dto.AiTransactionItem;
import com.finledger.ai.model.AnalysisData;
import com.finledger.ai.model.AnalysisIntent;
import com.finledger.ai.model.AnalysisType;
import com.finledger.ai.model.AnalysisWindow;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionAnalyticsService {

    private final TransactionRecordMapper transactionRecordMapper;

    public TransactionAnalyticsService(TransactionRecordMapper transactionRecordMapper) {
        this.transactionRecordMapper = transactionRecordMapper;
    }

    public AnalysisData analyze(Long userId, AnalysisIntent intent, AnalysisWindow window) {
        if (intent.type() == AnalysisType.LARGE_TRANSACTIONS) {
            List<AiTransactionItem> items = transactionRecordMapper.selectLargeTransactions(
                    userId, window.from(), window.to(), intent.threshold(), intent.limit()
            ).stream().map(this::toItem).toList();
            BigDecimal total = items.stream().map(AiTransactionItem::amount)
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            return new AnalysisData(total, items.size(), intent.threshold(), items);
        }

        BigDecimal total = transactionRecordMapper.sumOutgoing(userId, window.from(), window.to());
        long count = transactionRecordMapper.countOutgoing(userId, window.from(), window.to());
        int resultLimit = intent.type() == AnalysisType.MONTHLY_OUTGOING ? 5 : intent.limit();
        List<AiTransactionItem> items = transactionRecordMapper.selectTopOutgoing(
                userId, window.from(), window.to(), resultLimit
        ).stream().map(this::toItem).toList();
        return new AnalysisData(total, count, intent.threshold(), items);
    }

    private AiTransactionItem toItem(TransactionRecordEntity record) {
        return new AiTransactionItem(
                record.getRecordNo(), record.getAccountId(), record.getDirection(), record.getAmount(),
                record.getCurrency(), record.getCounterpartyAccountId(), record.getCreatedAt()
        );
    }
}
