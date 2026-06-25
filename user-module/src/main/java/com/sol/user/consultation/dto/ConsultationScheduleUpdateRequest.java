package com.sol.user.consultation.dto;

import java.time.LocalDateTime;

/** 상담 일정 변경 요청. */
public record ConsultationScheduleUpdateRequest(
        LocalDateTime scheduledAt
) {
}
