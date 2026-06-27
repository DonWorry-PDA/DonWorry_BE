package com.sol.user.assetconnection.domain;

import com.sol.user.assetconnection.entity.AssetConnection;

import java.util.Collection;

/**
 * "연결된 기관 수"의 단일 정의(#204).
 *
 * <p>연결된 기관 수 = {@code connectionStatus == CONNECTED}인 {@link AssetConnection}을
 * {@code institutionName} 기준으로 중복 제거한 distinct count(은행·증권·연금·보험·카드 전 도메인 포함).
 * 한 기관에 계좌·대출이 여러 행으로 있어도 회사(기관) 단위로는 1곳으로 센다(마이데이터 표준).
 *
 * <p>진실 소스는 {@code InstitutionCode} 카탈로그가 아니라 {@link AssetConnection} 행 목록이다.
 * 온보딩 응답과 마이페이지 요약이 이 함수만 호출해 항상 같은 값을 내도록 한다.
 */
public final class ConnectedInstitutions {

    private static final String CONNECTED = "CONNECTED";

    private ConnectedInstitutions() {
    }

    public static long count(Collection<AssetConnection> connections) {
        return connections.stream()
                .filter(c -> CONNECTED.equals(c.getConnectionStatus()))
                .map(AssetConnection::getInstitutionName)
                .distinct()
                .count();
    }
}
