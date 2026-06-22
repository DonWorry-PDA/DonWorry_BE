package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CurrencyExposure;

import java.math.BigDecimal;

/**
 * 안 내 개별 보유 종목. (현재 STEP5는 위험버킷 ETF만 명시 — 안전버킷은 PlanAllocation의 집계 금액으로 둠)
 */
public record Holding(
        String ticker,
        String productName,
        BucketRole role,
        CurrencyExposure currency,
        BigDecimal weight,   // 위험버킷 내 비중 (합=1.0)
        BigDecimal amount    // 금액
) {
}
