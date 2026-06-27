package com.sol.user.assetconnection.domain;

import com.sol.user.assetconnection.entity.AssetConnection;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectedInstitutionsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 27, 9, 0);

    private static AssetConnection conn(String name, String category, String status) {
        return new AssetConnection(null, name, category, status, NOW);
    }

    @Test
    void countsSameInstitutionAcrossCategoriesOnce() {
        // mock connect 직후 연결 6행 — 신한은행이 BANK+LOAN 두 행
        List<AssetConnection> connections = List.of(
                conn("신한은행", "BANK", "CONNECTED"),
                conn("신한투자증권", "SECURITIES", "CONNECTED"),
                conn("국민연금공단", "PENSION", "CONNECTED"),
                conn("신한라이프", "INSURANCE", "CONNECTED"),
                conn("신한카드", "CARD", "CONNECTED"),
                conn("신한은행", "LOAN", "CONNECTED")
        );

        // 신한은행 BANK+LOAN은 기관명 distinct로 1곳 → 5
        assertThat(ConnectedInstitutions.count(connections)).isEqualTo(5);
    }

    @Test
    void excludesNonConnectedRows() {
        List<AssetConnection> connections = List.of(
                conn("신한은행", "BANK", "CONNECTED"),
                conn("신한투자증권", "SECURITIES", "DISCONNECTED")
        );

        assertThat(ConnectedInstitutions.count(connections)).isEqualTo(1);
    }

    @Test
    void countsZeroWhenEmpty() {
        assertThat(ConnectedInstitutions.count(List.of())).isZero();
    }
}
