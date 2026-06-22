package com.sol.user.portfolio.infra.rest;

import java.util.List;

/**
 * product-module의 ApiResponse 래퍼 역직렬화용 클라이언트 record.
 * (공유 ApiResponse는 private 빌더 생성자라 직접 역직렬화가 까다로워 클라이언트 측에 별도 정의)
 */
public record EtfPoolApiResponse(
        String code,
        String message,
        List<EtfPoolItem> data
) {
}
