package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.PlanStatus;
import com.sol.user.portfolio.type.PlanType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 안별 추천 응답 — STEP5 배분(PlanAllocation)과 STEP6 수령(PlanCoverage)을 병합하고
 * 화면용 필드(displayName/status/allocations)까지 빚어 내려준다. 조립은 PortfolioRecommendationMapper.
 */
@Getter
@Builder
public class PlanResponse {

    private PlanType type;
    private String label;          // 도메인 라벨 (예: "안정안")
    private String displayName;    // 화면 표시명 (예: "안정 월급형")
    private String description;
    private PlanStatus status;

    // 배분 (STEP5)
    private BigDecimal riskTarget;
    private BigDecimal safeTarget;
    private BigDecimal shortTermBucket;
    private List<Holding> holdings;
    private List<AllocationView> allocations;   // 화면 배분 항목 (안전·위험·단기 병합, 비중 %)

    // 수령 (STEP6)
    private BigDecimal monthlyIncome;              // 총인출 기준 (원금소진+자본차익 포함)
    // 추천 운용으로 늘어나는 순월급 = (monthlyIncome − 현재월현금흐름).max(0). 국민연금은 양쪽 동일포함이라
    // 상쇄돼 순수 운용 기여만 남는다. 음수(이 안 고르면 현재보다 줄어듦)는 0으로 막고 부족액 지표로 위임.
    private BigDecimal incrementalMonthlyIncome;
    private BigDecimal alphaCoverageRate;          // α충족률 = 총인출 기준 (α≤0이면 null, 100캡)
    private BigDecimal sustainableCoverageRate;    // α충족률 = 지속가능 기준 (이자·배당만, 100캡)
    private BigDecimal inheritanceAmount;
    private BigDecimal shortTermLumpSum;           // 단기 목돈 (유동성안이 따로 확보한 일회성 인출분, 월수령·상속과 별개)

    // 화면 비교 표시 (예: "충당 59% → 84%", "부족분 90만 → 35만")
    private BigDecimal totalCoverageRate;          // 설계안 적용 후 생활비 충당률 % (monthlyIncome / targetLivingCost)
    private BigDecimal residualMonthlyShortfall;   // 설계안 적용 후 월 부족액 (0이면 초과 달성)
}
