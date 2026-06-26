package com.sol.user.monthlysalary.mapper;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryAssetMapperTest {

    private final SalaryAssetMapper mapper = new SalaryAssetMapper();

    @Test
    void 제외목록에서_계좌_ID와_종목_ID를_접두사로_갈라_추출한다() {
        Set<String> keys = Set.of("ACCOUNT_6", "HOLDING_121", "HOLDING_122", "ACCOUNT_8");

        // 저장된 키 포맷(createAccountAssetKey/createHoldingAssetKey)과 라운드트립 — 死선 재발 방지 가드
        assertThat(mapper.extractAccountIds(keys)).containsExactlyInAnyOrder(6L, 8L);
        assertThat(mapper.extractHoldingIds(keys)).containsExactlyInAnyOrder(121L, 122L);
    }

    @Test
    void 빈_제외목록이나_null이면_빈_집합을_반환한다() {
        assertThat(mapper.extractAccountIds(Set.of())).isEmpty();
        assertThat(mapper.extractHoldingIds(null)).isEmpty();
    }

    @Test
    void 접두사가_틀리거나_숫자가_아니면_조용히_무시한다() {
        Set<String> keys = Set.of("ACCOUNT_abc", "UNKNOWN_1", "HOLDING_");

        assertThat(mapper.extractAccountIds(keys)).isEmpty();
        assertThat(mapper.extractHoldingIds(keys)).isEmpty();
    }
}
