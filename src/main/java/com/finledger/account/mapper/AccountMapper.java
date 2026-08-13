package com.finledger.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finledger.account.entity.AccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
