package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CurrencyExposure;

import java.math.BigDecimal;

/**
 * 안 내 개별 보유 종목. STEP5에서 위험·안전·단기버킷 모두 개별 종목으로 분해된다(role로 버킷 구분).
 */
public record Holding(
        String ticker,
        String productName,
        BucketRole role,
        CurrencyExposure currency,
        BigDecimal weight,   // 버킷 내 비중 (각 버킷 합=1.0)
        BigDecimal amount    // 금액
) {
}
