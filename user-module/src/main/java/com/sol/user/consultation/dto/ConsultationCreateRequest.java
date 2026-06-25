package com.sol.user.consultation.dto;

import com.sol.user.consultation.type.ConsultType;

import java.time.LocalDateTime;

/** 상담 예약 생성 요청. method/branch/counselor는 BE가 유형별 기본값으로 배정한다. */
public record ConsultationCreateRequest(
        ConsultType consultType,
        LocalDateTime scheduledAt,
        Long planId
) {
}
