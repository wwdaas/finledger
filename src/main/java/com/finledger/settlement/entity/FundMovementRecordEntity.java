package com.finledger.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("fund_movement_record")
public class FundMovementRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String movementNo;
    private Long accountId;
    private Long userId;
    private String businessType;
    private Long businessId;
    private String action;
    private BigDecimal amount;
    private BigDecimal availableBefore;
    private BigDecimal availableAfter;
    private BigDecimal frozenBefore;
    private BigDecimal frozenAfter;
    private BigDecimal totalBefore;
    private BigDecimal totalAfter;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMovementNo() { return movementNo; }
    public void setMovementNo(String movementNo) { this.movementNo = movementNo; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getAvailableBefore() { return availableBefore; }
    public void setAvailableBefore(BigDecimal availableBefore) { this.availableBefore = availableBefore; }
    public BigDecimal getAvailableAfter() { return availableAfter; }
    public void setAvailableAfter(BigDecimal availableAfter) { this.availableAfter = availableAfter; }
    public BigDecimal getFrozenBefore() { return frozenBefore; }
    public void setFrozenBefore(BigDecimal frozenBefore) { this.frozenBefore = frozenBefore; }
    public BigDecimal getFrozenAfter() { return frozenAfter; }
    public void setFrozenAfter(BigDecimal frozenAfter) { this.frozenAfter = frozenAfter; }
    public BigDecimal getTotalBefore() { return totalBefore; }
    public void setTotalBefore(BigDecimal totalBefore) { this.totalBefore = totalBefore; }
    public BigDecimal getTotalAfter() { return totalAfter; }
    public void setTotalAfter(BigDecimal totalAfter) { this.totalAfter = totalAfter; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
