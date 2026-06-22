package com.sol.user.portfolio.provider;

import com.sol.user.portfolio.dto.EtfInfo;

import java.util.List;

/**
 * 추천 풀(화이트리스트 26종) 공급 포트. 데이터 출처(REST/DB)를 추상화한다.
 * 현재 구현은 REST(A1) — product-module 풀조회 API 호출. 추후 A1 승격 시 이 인터페이스는 불변.
 */
public interface EtfPoolProvider {

    List<EtfInfo> getPool();
}
