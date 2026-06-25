package com.sol.user.consultation.dto;

import com.sol.user.consultation.entity.Consultation;
import com.sol.user.consultation.type.ConsultMethod;
import lombok.Builder;

import java.time.LocalDateTime;

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
                // 대면이면 지점명, 비대면이면 방식 라벨("비대면 상담")을 location으로 내려 FE가 바로 표시
                .location(c.getMethod() == ConsultMethod.FACE_TO_FACE
                        ? c.getBranchName()
                        : c.getMethod().getLabel())
                .counselorName(c.getCounselorName())
                .planId(c.getPlanId())
                .hasSummary(hasSummary)
                .build();
    }
}
