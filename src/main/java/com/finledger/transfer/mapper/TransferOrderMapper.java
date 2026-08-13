package com.finledger.transfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finledger.transfer.entity.TransferOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Mapper
public interface TransferOrderMapper extends BaseMapper<TransferOrderEntity> {

    @Select("""
            SELECT *
            FROM transfer_order
            WHERE id = #{transferId}
            FOR UPDATE
            """)
    TransferOrderEntity selectByIdForUpdate(@Param("transferId") Long transferId);

    @Update("""
            UPDATE transfer_order
            SET status = #{targetStatus}, completed_at = #{completedAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{transferId} AND status = 'PENDING'
            """)
    int transitionPending(
            @Param("transferId") Long transferId,
            @Param("targetStatus") String targetStatus,
            @Param("completedAt") LocalDateTime completedAt
    );

    @Select("""
            SELECT COALESCE(SUM(amount), 0.00)
            FROM transfer_order
            WHERE initiator_user_id = #{userId}
              AND status IN ('SUCCESS', 'PENDING', 'SETTLED')
              AND created_at >= #{from}
              AND created_at < #{to}
            """)
    BigDecimal sumAcceptedOutgoing(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Update("""
            UPDATE transfer_order
            SET status = #{status}, risk_decision = #{riskDecision},
                completed_at = #{completedAt}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{transferId} AND status = 'PROCESSING'
            """)
    int completeRiskAssessment(
            @Param("transferId") Long transferId,
            @Param("status") String status,
            @Param("riskDecision") String riskDecision,
            @Param("completedAt") LocalDateTime completedAt
    );
}
