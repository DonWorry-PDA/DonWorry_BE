package com.sol.user.mydata.dto;

import java.util.List;

/**
 * 연결된 기관 목록 + 카운트 동봉(#211).
 *
 * <p>카운트와 목록을 한 응답에 담아 {@code connectedInstitutionCount == institutions.size()}를
 * API 레벨에서 보장한다(온보딩 카운트와 리스트 모집단 불일치를 구조적으로 차단).
 */
public record ConnectedInstitutionsResponse(
        int connectedInstitutionCount,
        List<ConnectedInstitutionResponse> institutions
) {
    public static ConnectedInstitutionsResponse of(List<ConnectedInstitutionResponse> institutions) {
        return new ConnectedInstitutionsResponse(institutions.size(), institutions);
    }
}
