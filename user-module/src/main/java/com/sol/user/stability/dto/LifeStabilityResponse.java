package com.sol.user.stability.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record LifeStabilityResponse(
        String grade,
        String gradeLabel,
        String summaryMessage,
        LifeStabilityMetrics metrics,
        LifeStabilityIndicators indicators,
        PlanGuardrailResponse planGuardrail,
        List<String> improvementMessages
) {
}
