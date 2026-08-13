package com.finledger.transfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finledger.transfer.entity.TransferOrderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransferOrderMapper extends BaseMapper<TransferOrderEntity> {
}
