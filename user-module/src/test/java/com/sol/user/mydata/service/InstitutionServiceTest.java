package com.sol.user.mydata.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.assetconnection.entity.AssetConnection;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.mydata.dto.ConnectedInstitutionCountResponse;
import com.sol.user.mydata.dto.ConnectedInstitutionResponse;
import com.sol.user.mydata.dto.ConnectedInstitutionsResponse;
import com.sol.user.mydata.dto.InstitutionResponse;
import com.sol.user.user.repository.UserRepository;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock AssetConnectionRepository assetConnectionRepository;
    @Mock UserRepository userRepository;

    @InjectMocks InstitutionService institutionService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 27, 9, 0);

    private static AssetConnection conn(String name, String category) {
        return new AssetConnection(null, name, category, "CONNECTED", NOW);
    }

    @Test
    void connectedInstitutionCountMatchesOnboardingDefinition() {
        // 온보딩 mock과 동일한 6행(신한은행 BANK+LOAN 포함) → 기관명 distinct 5 (#204)
        when(assetConnectionRepository.findByUserUserId(1L)).thenReturn(List.of(
                conn("신한은행", "BANK"),
                conn("신한투자증권", "SECURITIES"),
                conn("국민연금공단", "PENSION"),
                conn("신한라이프", "INSURANCE"),
                conn("신한카드", "CARD"),
                conn("신한은행", "LOAN")
        ));

        ConnectedInstitutionCountResponse response = institutionService.getConnectedInstitutionCount(1L);

        assertThat(response.connectedInstitutionCount()).isEqualTo(5);
    }

    @Test
    void connectedInstitutionsMergesMultiCategoryAndMatchesCount() {
        // 온보딩 mock과 동일한 6행: 신한은행 BANK+LOAN → 1행 병합, distinct 5 (#211)
        when(assetConnectionRepository.findByUserUserId(1L)).thenReturn(List.of(
                conn("신한은행", "BANK"),
                conn("신한투자증권", "SECURITIES"),
                conn("국민연금공단", "PENSION"),
                conn("신한라이프", "INSURANCE"),
                conn("신한카드", "CARD"),
                conn("신한은행", "LOAN")
        ));

        ConnectedInstitutionsResponse response = institutionService.getConnectedInstitutions(1L);

        // 길이 == connected-count(5) 불변식: count는 항상 institutions.size()와 같다
        assertThat(response.institutions()).hasSize(5);
        assertThat(response.connectedInstitutionCount()).isEqualTo(response.institutions().size());

        // 신한은행은 BANK+LOAN이 1행으로 합쳐지고 대표 category는 우선순위 높은 BANK
        assertThat(response.institutions())
                .filteredOn(i -> "신한은행".equals(i.name()))
                .singleElement()
                .satisfies(i -> assertThat(i.category()).isEqualTo("BANK"));

        // 정렬: 은행·증권 우선 → 연금·보험·카드
        assertThat(response.institutions()).extracting(ConnectedInstitutionResponse::category)
                .containsExactly("BANK", "SECURITIES", "PENSION", "INSURANCE", "CARD");

        // 모든 항목에 렌더용 메타가 비어있지 않다(카탈로그 매칭 + category 폴백 양쪽)
        assertThat(response.institutions()).allSatisfy(i -> {
            assertThat(i.status()).isEqualTo("CONNECTED");
            assertThat(i.label()).isNotBlank();
            assertThat(i.brandColor()).isNotBlank();
            assertThat(i.labelColor()).isNotBlank();
        });

        // 카탈로그에 없는 연금은 category 폴백 라벨("연금")을 쓴다
        assertThat(response.institutions())
                .filteredOn(i -> "국민연금공단".equals(i.name()))
                .singleElement()
                .satisfies(i -> assertThat(i.label()).isEqualTo("연금"));
    }

    @Test
    void connectedFlagDerivesFromAssetConnectionNotAccountPresence() {
        // 신한은행: 연결 행 있음 → connected. 신한투자증권: 계좌만 있고 연결 행 없음 → NOT connected (#204)
        when(assetConnectionRepository.findByUserUserId(1L)).thenReturn(List.of(
                conn("신한은행", "BANK")
        ));
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of(
                new Account(null, "BROKERAGE", "신한투자증권", "MOCK-1-BROKERAGE",
                        BigDecimal.valueOf(1_000_000), true)
        ));

        List<InstitutionResponse> institutions = institutionService.getInstitutions(1L);

        assertThat(institutions)
                .filteredOn(i -> "신한은행".equals(i.name()))
                .singleElement()
                .satisfies(i -> assertThat(i.connected()).isTrue());
        // 계좌만 있는 기관은 연결로 치지 않는다 — 연결의 진실 소스는 AssetConnection뿐
        assertThat(institutions)
                .filteredOn(i -> "신한투자증권".equals(i.name()))
                .singleElement()
                .satisfies(i -> assertThat(i.connected()).isFalse());
    }
}
