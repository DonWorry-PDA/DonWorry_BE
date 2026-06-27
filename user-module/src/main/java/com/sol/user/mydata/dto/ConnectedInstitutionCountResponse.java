package com.sol.user.mydata.dto;

/**
 * 마이페이지 "연결된 기관 수" 요약(#204).
 * 기존 {@code GET /institutions}(기관 배열)은 그대로 두고, 헤드라인 카운트만 별도로 제공한다.
 */
public record ConnectedInstitutionCountResponse(int connectedInstitutionCount) {
}
