package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.InvestmentPropensity;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * STEP5 배분 계산기 입력. 오케스트레이터가 STEP1~4 결과(OperationGradeResult)와
 * 원본 입력·설정·풀을 조립해 만든다.
 */
@Builder
public record AllocationInput(
        int finalGrade,                  // STEP4 최종 등급 (1~5)
        BigDecimal surplus,              // 여유분 (pinnedSafe 이미 제외됨)
        BigDecimal floorAsset,           // 바닥자산
        BigDecimal totalAsset,           // 총자산 (pinnedSafe 포함)
        BigDecimal pensionSaving,        // 연금저축 (별도계좌)
        BigDecimal pinnedSafe,           // 정기예금 — safeTarget·maxBuyTotal 차감용
        InvestmentPropensity propensity, // 성향 (tier·권유등급)
        BigDecimal shortTermBucket,      // 유동성안 단기버킷 (null/0이면 여유분×0.10)
        List<EtfInfo> pool               // 추천 풀
) {
}
