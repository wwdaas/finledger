package com.finledger.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.common.api.PageResponse;
import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.risk.entity.RiskEventEntity;
import com.finledger.risk.mapper.RiskEventMapper;
import com.finledger.risk.model.RiskDecision;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RiskEventQueryService {

    private final RiskEventMapper riskEventMapper;
    private final ObjectMapper objectMapper;

    public RiskEventQueryService(RiskEventMapper riskEventMapper, ObjectMapper objectMapper) {
        this.riskEventMapper = riskEventMapper;
        this.objectMapper = objectMapper;
    }

    public PageResponse<RiskEventResponse> query(
            Long userId,
            Long transactionId,
            String businessNo,
            RiskDecision decision,
            String ruleCode,
            long pageNumber,
            long pageSize
    ) {
        LambdaQueryWrapper<RiskEventEntity> query =
                new LambdaQueryWrapper<RiskEventEntity>()
                        .eq(RiskEventEntity::getUserId, userId)
                        .eq(transactionId != null, RiskEventEntity::getBusinessId, transactionId)
                        .eq(businessNo != null && !businessNo.isBlank(),
                                RiskEventEntity::getBusinessNo, businessNo)
                        .eq(decision != null, RiskEventEntity::getDecision,
                                decision == null ? null : decision.name())
                        .eq(ruleCode != null && !ruleCode.isBlank(),
                                RiskEventEntity::getRuleCode, ruleCode)
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

    public PageResponse<RiskEventResponse> query(
            Long userId,
            String businessNo,
            long pageNumber,
            long pageSize
    ) {
        return query(userId, null, businessNo, null, null, pageNumber, pageSize);
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
                event.getId(), event.getBusinessId(), event.getBusinessNo(), event.getRuleCode(),
                event.getRiskLevel(), event.getDecision(), event.getAmount(), event.getReason(),
                readMetadata(event.getMetadataJson()), event.getCreatedAt()
        );
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored risk metadata is invalid JSON", exception);
        }
    }
}
