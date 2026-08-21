package com.finledger.idempotency.service;

import com.finledger.common.money.MoneyAmounts;
import com.finledger.transfer.dto.TransferRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class IdempotencyRequestHasher {

    public String hashTransfer(TransferRequest request) {
        String canonicalRequest = request.fromAccountId()
                + "|" + request.toAccountId()
                + "|" + MoneyAmounts.requirePositive(request.amount()).toPlainString();
        return sha256(canonicalRequest);
    }

    public String hashFreeze(
            Long accountId,
            java.math.BigDecimal amount,
            String businessType,
            String remark
    ) {
        String normalizedRemark = remark == null ? "" : remark;
        String canonicalRequest = accountId
                + "|" + MoneyAmounts.requirePositive(amount).toPlainString()
                + "|" + businessType.length() + ":" + businessType
                + "|" + normalizedRemark.length() + ":" + normalizedRemark;
        return sha256(canonicalRequest);
    }

    private String sha256(String canonicalRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
