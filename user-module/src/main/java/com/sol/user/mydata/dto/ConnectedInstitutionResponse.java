package com.sol.user.mydata.dto;

/**
 * 연결된 기관 1곳(#211). 진실 소스는 {@code AssetConnection}이며, 표시 메타(label/색)는
 * 기관명이 {@code InstitutionCode} 카탈로그와 매칭되면 카탈로그값, 아니면 category 폴백을 쓴다.
 *
 * @param status 연결 상태(현재는 모두 CONNECTED, 추후 연결 실패 표현 대비)
 * @param category 대표 도메인 카테고리(BANK·SECURITIES·PENSION·INSURANCE·CARD·LOAN)
 */
public record ConnectedInstitutionResponse(
        String name,
        String category,
        String status,
        String label,
        String brandColor,
        String labelColor
) {
}
