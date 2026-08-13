package com.finledger.transfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finledger.transfer.entity.TransferOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

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
}
