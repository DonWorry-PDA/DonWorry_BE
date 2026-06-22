package com.sol.product.etf.dto;

import com.sol.product.etf.entity.EtfDetail;

import java.math.BigDecimal;

/**
 * 포트폴리오 추천(STEP5)용 경량 ETF 조회 DTO.
 * 계산에 필요한 최소 필드만 노출 — 배당이력(DividendHistory) 조인 없이 etf_detail 단독 매핑.
 */
public record EtfPoolItem(
        String ticker,
        String productName,
        Integer riskGrade,
        BigDecimal annualDividendRate,
        String distributionCycle
) {

    public static EtfPoolItem from(EtfDetail etfDetail) {
        return new EtfPoolItem(
                etfDetail.getTickerCode(),
                etfDetail.getProduct().getProductName(),
                etfDetail.getRiskGrade(),
                etfDetail.getAnnualDividendRate(),
                etfDetail.getDistributionCycle()
        );
    }
}
