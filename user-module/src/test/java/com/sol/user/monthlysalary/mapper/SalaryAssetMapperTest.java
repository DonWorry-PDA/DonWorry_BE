package com.sol.user.monthlysalary.mapper;

import com.sol.user.account.entity.Account;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.monthlysalary.dto.AssetItemDto;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

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

    // ── deployability 계약(재료 화면 3계층) 가드 ─────────────────────────────────

    @Test
    void 정기예금_계좌는_PINNED_SAFE_그_외는_FREE_연금은_RESTRICTED_PENSION() {
        assertThat(mapper.toAccountItem(account("DEPOSIT"), Set.of(), null).getDeployability())
                .isEqualTo(AssetItemDto.Deployability.PINNED_SAFE);
        assertThat(mapper.toAccountItem(account("IRP"), Set.of(), null).getDeployability())
                .isEqualTo(AssetItemDto.Deployability.RESTRICTED_PENSION);
        assertThat(mapper.toAccountItem(account("PENSION_SAVING"), Set.of(), null).getDeployability())
                .isEqualTo(AssetItemDto.Deployability.RESTRICTED_PENSION);
        assertThat(mapper.toAccountItem(account("BROKERAGE"), Set.of(), null).getDeployability())
                .isEqualTo(AssetItemDto.Deployability.FREE);
    }

    @Test
    void 개별주식_보유종목은_계좌타입과_무관하게_EXCLUDED_STOCK() {
        // STOCK 판정이 연금계좌보다 우선 — 청산 전제라 월급 재료에서 빠지는 것과 계약 일치.
        ProductBatchItem stock = new ProductBatchItem(10L, "삼성전자", "STOCK", "005930");
        assertThat(mapper.toHoldingItem(holding("BROKERAGE"), stock, Set.of()).getDeployability())
                .isEqualTo(AssetItemDto.Deployability.EXCLUDED_STOCK);
        assertThat(mapper.toHoldingItem(holding("IRP"), stock, Set.of()).getDeployability())
                .isEqualTo(AssetItemDto.Deployability.EXCLUDED_STOCK);
    }

    @Test
    void 연금계좌_ETF는_RESTRICTED_PENSION_비연금_ETF는_FREE() {
        ProductBatchItem etf = new ProductBatchItem(20L, "SOL 미국배당다우존스", "ETF", "446720");
        assertThat(mapper.toHoldingItem(holding("PENSION_SAVING"), etf, Set.of()).getDeployability())
                .isEqualTo(AssetItemDto.Deployability.RESTRICTED_PENSION);
        assertThat(mapper.toHoldingItem(holding("IRP"), etf, Set.of()).getDeployability())
                .isEqualTo(AssetItemDto.Deployability.RESTRICTED_PENSION);
        assertThat(mapper.toHoldingItem(holding("BROKERAGE"), etf, Set.of()).getDeployability())
                .isEqualTo(AssetItemDto.Deployability.FREE);
    }

    private Account account(String accountType) {
        return new Account(null, accountType, "신한", "123", BigDecimal.ZERO, false);
    }

    private HoldingWithProduct holding(String accountType) {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        lenient().when(holding.getHoldingId()).thenReturn(1L);
        lenient().when(holding.getEvaluationAmount()).thenReturn(BigDecimal.valueOf(1_000_000));
        lenient().when(holding.getAccountType()).thenReturn(accountType);
        return holding;
    }
}
