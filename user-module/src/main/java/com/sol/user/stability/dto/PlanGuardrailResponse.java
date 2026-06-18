package com.sol.user.stability.dto;

import lombok.Builder;

@Builder
public record PlanGuardrailResponse(
        boolean growthPlanAllowed,
        String recommendedPlanType,
        String recommendedPlanLabel,
        String reason
) {
}
