package com.finledger.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finledger.ledger.entity.TransactionRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TransactionRecordMapper extends BaseMapper<TransactionRecordEntity> {

    @Select("""
            SELECT COALESCE(SUM(amount), 0.00)
            FROM transaction_record
            WHERE user_id = #{userId}
              AND business_type = 'TRANSFER'
              AND direction = 'DEBIT'
              AND created_at >= #{from}
              AND created_at < #{to}
            """)
    BigDecimal sumOutgoing(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Select("""
            SELECT COUNT(*)
            FROM transaction_record
            WHERE user_id = #{userId}
              AND business_type = 'TRANSFER'
              AND direction = 'DEBIT'
              AND created_at >= #{from}
              AND created_at < #{to}
            """)
    long countOutgoing(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Select("""
            SELECT *
            FROM transaction_record
            WHERE user_id = #{userId}
              AND business_type = 'TRANSFER'
              AND direction = 'DEBIT'
              AND created_at >= #{from}
              AND created_at < #{to}
            ORDER BY amount DESC, created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<TransactionRecordEntity> selectTopOutgoing(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("limit") int limit
    );

    @Select("""
            SELECT *
            FROM transaction_record
            WHERE user_id = #{userId}
              AND amount >= #{threshold}
              AND created_at >= #{from}
              AND created_at < #{to}
            ORDER BY amount DESC, created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<TransactionRecordEntity> selectLargeTransactions(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("threshold") BigDecimal threshold,
            @Param("limit") int limit
    );
}
