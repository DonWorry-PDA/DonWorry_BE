package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.PlanType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 한 안(STABLE/BALANCED/LIQUIDITY)의 배분 결과.
 */
@Getter
@Builder
public class PlanAllocation {

    private PlanType type;

    private BigDecimal riskTarget;          // 위험목표 금액
    private BigDecimal safeTarget;          // 안전목표 금액
    private BigDecimal surplusRiskAmount;   // 여유위험액 (= riskTarget)
    private BigDecimal surplusSafeAmount;   // 여유안전액
    private BigDecimal shortTermBucket;     // 단기버킷 (유동성안만 > 0)
    private BigDecimal planDividendRate;    // 위험보유 가중평균 배당률 (STEP6용)
    private BigDecimal riskCapGainTaxableWeight; // 위험버킷 자본차익 과세분 가중 (국내주식형=0·해외=1, STEP6용). null이면 1.0(전액 과세) 폴백

    private List<Holding> holdings;         // 전체 개별 보유 종목 (안전·위험·단기버킷 병합, role로 구분)
}
