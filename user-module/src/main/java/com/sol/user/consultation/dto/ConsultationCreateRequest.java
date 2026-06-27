package com.sol.user.consultation.dto;

import com.sol.user.consultation.type.ConsultType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담 예약 생성 요청. method/branch/counselor는 BE가 유형별 기본값으로 배정한다.
 * topic/contextTopics는 진입 맥락(어떤 화면에서 무슨 내용으로 신청했는지)으로, FE가 보낸 값을 그대로 저장한다.
 * topic이 없으면 consultType별 기본 제목으로 폴백한다.
 */
public record ConsultationCreateRequest(
        ConsultType consultType,
        LocalDateTime scheduledAt,
        Long planId,
        String topic,
        List<String> contextTopics
) {
}
