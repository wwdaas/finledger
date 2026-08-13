package com.finledger.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finledger.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("""
            SELECT id, username, password_hash, status, created_at, updated_at
            FROM sys_user
            WHERE id = #{userId}
            FOR UPDATE
            """)
    UserEntity selectByIdForUpdate(@Param("userId") Long userId);
}
