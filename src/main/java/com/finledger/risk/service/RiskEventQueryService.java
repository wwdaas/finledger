package com.finledger.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finledger.common.api.PageResponse;
import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.risk.entity.RiskEventEntity;
import com.finledger.risk.mapper.RiskEventMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskEventQueryService {

    private final RiskEventMapper riskEventMapper;

    public RiskEventQueryService(RiskEventMapper riskEventMapper) {
        this.riskEventMapper = riskEventMapper;
    }

    public PageResponse<RiskEventResponse> query(
            Long userId,
            String businessNo,
            long pageNumber,
            long pageSize
    ) {
        LambdaQueryWrapper<RiskEventEntity> query =
                new LambdaQueryWrapper<RiskEventEntity>()
                        .eq(RiskEventEntity::getUserId, userId)
                        .eq(businessNo != null && !businessNo.isBlank(),
                                RiskEventEntity::getBusinessNo, businessNo)
                        .orderByDesc(RiskEventEntity::getCreatedAt)
                        .orderByDesc(RiskEventEntity::getId);
        Page<RiskEventEntity> result = riskEventMapper.selectPage(
                new Page<>(pageNumber, pageSize), query
        );
        List<RiskEventResponse> items = result.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(
                items, result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()
        );
    }

    public List<RiskEventResponse> findByBusinessNo(Long userId, String businessNo) {
        return riskEventMapper.selectList(
                new LambdaQueryWrapper<RiskEventEntity>()
                        .eq(RiskEventEntity::getUserId, userId)
                        .eq(RiskEventEntity::getBusinessNo, businessNo)
                        .orderByAsc(RiskEventEntity::getId)
        ).stream().map(this::toResponse).toList();
    }

    private RiskEventResponse toResponse(RiskEventEntity event) {
        return new RiskEventResponse(
                event.getId(), event.getBusinessNo(), event.getRuleCode(), event.getRiskLevel(),
                event.getDecision(), event.getReason(), event.getCreatedAt()
        );
    }
}
