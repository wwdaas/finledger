package com.finledger.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finledger.account.entity.AccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountMapper extends BaseMapper<AccountEntity> {

    @Select("""
            SELECT id, user_id, account_no, available_balance, frozen_balance,
                   currency, status, version,
                   created_at, updated_at
            FROM account
            WHERE id = #{accountId}
            FOR UPDATE
            """)
    AccountEntity selectByIdForUpdate(@Param("accountId") Long accountId);

    @Update("""
            UPDATE account
            SET available_balance = #{newAvailableBalance},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{accountId} AND version = #{expectedVersion}
            """)
    int updateAvailableBalanceWithVersion(
            @Param("accountId") Long accountId,
            @Param("newAvailableBalance") java.math.BigDecimal newAvailableBalance,
            @Param("expectedVersion") Long expectedVersion
    );
}
