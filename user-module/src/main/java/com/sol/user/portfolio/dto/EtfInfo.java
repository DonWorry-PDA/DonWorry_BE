package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CurrencyExposure;

import java.math.BigDecimal;

/**
 * 계산기가 보는 유일한 상품 표현(경계 DTO). JPA 엔티티/외부 응답과 분리돼,
 * 데이터 출처(REST/DB)가 바뀌어도 계산기는 무관하다.
 * role·currency는 provider가 PortfolioConstants 매핑으로 합성해 채운다.
 */
public record EtfInfo(
        Long productId,
        String ticker,
        String productName,
        int riskGrade,
        BigDecimal annualDividendRate,
        String distributionCycle,
        BucketRole role,
        CurrencyExposure currency
) {
}
