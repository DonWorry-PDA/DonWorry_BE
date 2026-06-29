package com.sol.user.consultation.dto;

import com.sol.user.consultation.entity.Consultation;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ConsultationResponse(
        Long id,
        String title,
        String consultType,
        String status,
        LocalDateTime scheduledAt,
        String method,
        String location,
        String counselorName,
        Long planId,
        List<String> contextTopics,
        boolean hasSummary
) {
    public static ConsultationResponse from(Consultation c, boolean hasSummary) {
        return ConsultationResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .consultType(c.getConsultType().name())
                .status(c.getStatus().name())
                .scheduledAt(c.getScheduledAt())
                .method(c.getMethod().name())
                // 지점이 있으면(대면·전화 모두 진입점에서 지점을 고름) 지점명을, 없으면 방식 라벨을 내려 FE가 바로 표시
                .location(c.getBranchName() != null
                        ? c.getBranchName()
                        : c.getMethod().getLabel())
                .counselorName(c.getCounselorName())
                .planId(c.getPlanId())
                .contextTopics(c.getContextTopics())
                .hasSummary(hasSummary)
                .build();
    }
}
