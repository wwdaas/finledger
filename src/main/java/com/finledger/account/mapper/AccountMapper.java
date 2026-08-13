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
            SELECT id, user_id, account_no, balance, currency, status, version,
                   created_at, updated_at
            FROM account
            WHERE id = #{accountId}
            FOR UPDATE
            """)
    AccountEntity selectByIdForUpdate(@Param("accountId") Long accountId);

    @Update("""
            UPDATE account
            SET balance = #{newBalance}, version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{accountId} AND version = #{expectedVersion}
            """)
    int updateBalanceWithVersion(
            @Param("accountId") Long accountId,
            @Param("newBalance") java.math.BigDecimal newBalance,
            @Param("expectedVersion") Long expectedVersion
    );
}
