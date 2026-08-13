package com.finledger.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finledger.ledger.entity.TransactionRecordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransactionRecordMapper extends BaseMapper<TransactionRecordEntity> {
}
