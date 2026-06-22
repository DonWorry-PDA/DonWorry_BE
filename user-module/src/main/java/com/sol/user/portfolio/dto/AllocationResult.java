package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.RecommendationTrack;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * STEP5 배분 결과. track으로 분기를 표현(D2):
 * NORMAL이면 plans 2~3개, STRUCTURAL_SHORTAGE이면 plans 비움.
 * (PENSION_SUFFICIENT는 α를 보는 STEP6에서 판정)
 */
@Getter
@Builder
public class AllocationResult {

    private RecommendationTrack track;
    private List<PlanAllocation> plans;
}
