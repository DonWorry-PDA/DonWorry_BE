package com.sol.user.portfolio.infra.rest;

import java.math.BigDecimal;

/**
 * product-module 풀조회 API 응답 역직렬화용 클라이언트 record.
 * (product 측 DTO를 공유하지 않고 동일 필드를 복제 — 모듈 간 결합 회피)
 */
public record EtfPoolItem(
        Long productId,
        String ticker,
        String productName,
        Integer riskGrade,
        BigDecimal annualDividendRate,
        String distributionCycle
) {
}
