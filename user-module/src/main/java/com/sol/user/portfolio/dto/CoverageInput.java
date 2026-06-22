package com.sol.user.portfolio.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * STEP6 α충족률·소진모델 계산기 입력. 오케스트레이터가 STEP5 결과(AllocationResult)와
 * STEP1~4 결과·원본 입력을 조립해 만든다. (상품데이터 무관 순수 수치)
 */
@Builder
public record CoverageInput(
        AllocationResult allocation,     // STEP5 배분 결과 (track + plans)
        int q3,                          // 상속 vs 소비 (0~2)
        int age,                         // 연금저축 인출 가능 여부 판정
        int remainingYears,              // 남은햇수 (STEP1)
        BigDecimal targetLivingCost,     // 목표생활비
        BigDecimal monthlyNationalPension, // 국민연금
        BigDecimal otherRegularIncome,   // 기타정기수입 (α에만 반영)
        BigDecimal floorAsset,           // 바닥자산 (원금보존, 이자만)
        BigDecimal pensionSaving         // 연금저축
) {
}
