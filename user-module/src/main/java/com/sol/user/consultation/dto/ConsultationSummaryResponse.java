package com.sol.user.consultation.dto;

import com.sol.user.consultation.entity.ConsultationSummary;
import lombok.Builder;

import java.util.List;

@Builder
public record ConsultationSummaryResponse(
        String diagnosis,
        List<String> recommendations,
        List<String> nextSteps,
        String memo
) {
    public static ConsultationSummaryResponse of(ConsultationSummary summary, String memo) {
        return ConsultationSummaryResponse.builder()
                .diagnosis(summary.getDiagnosis())
                .recommendations(summary.getRecommendations())
                .nextSteps(summary.getNextSteps())
                .memo(memo)
                .build();
    }
}
