package com.sol.user.consultation.dto;

import com.sol.user.consultation.type.ConsultMethod;
import com.sol.user.consultation.type.ConsultType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담 예약 생성 요청.
 * branchId는 사용자가 진입점(목적별 통장→은행 / 월급 만들기→증권)별로 고른 영업점이며,
 * BE가 조회해 지점명·기관을 확정한다. branchId가 없으면 유형별 기본 지점으로 폴백한다.
 * method가 없으면 유형별 기본 방식으로 폴백한다(대면/전화).
 * topic/contextTopics는 진입 맥락(어떤 화면에서 무슨 내용으로 신청했는지)으로, FE가 보낸 값을 그대로 저장한다.
 * topic이 없으면 consultType별 기본 제목으로 폴백한다.
 */
public record ConsultationCreateRequest(
        ConsultType consultType,
        LocalDateTime scheduledAt,
        Long planId,
        String topic,
        List<String> contextTopics,
        Long branchId,
        ConsultMethod method
) {
}
