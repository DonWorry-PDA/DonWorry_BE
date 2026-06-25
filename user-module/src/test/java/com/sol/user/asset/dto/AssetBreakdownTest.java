package com.sol.user.asset.dto;

import com.sol.user.asset.dto.AssetBreakdown.AssetRole;
import com.sol.user.asset.dto.AssetBreakdown.RoleSlice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4역할 분해·현금흐름 비율의 단일 출처({@link AssetBreakdown#roleAllocation()} /
 * {@link AssetBreakdown#cashflowAssetRatio()}) 계약을 고정한다. 투자 건강검진 헤드라인·도넛과
 * 허브 미리보기가 모두 이 메서드를 통해 같은 숫자를 받으므로, 여기서 정합이 깨지면 화면 숫자가 어긋난다.
 */
class AssetBreakdownTest {

    @Test
    void 순자산_0이면_roleAllocation_빈리스트_cashflowAssetRatio_null() {
        AssetBreakdown breakdown = breakdown(0, 0, 0, 0, 0);

        assertThat(breakdown.roleAllocation()).isEmpty();
        assertThat(breakdown.cashflowAssetRatio()).isNull();
    }

    @Test
    void 자산은_있으나_현금흐름자산이_없으면_cashflowAssetRatio_0() {
        // 잠자는 돈 5천 + 성장 5천만, 현금흐름(비STOCK 보유) 0.
        AssetBreakdown breakdown = breakdown(50_000_000, 0, 0, 0, 50_000_000);

        assertThat(breakdown.cashflowAssetRatio()).isZero();
        assertThat(breakdown.roleAllocation()).extracting(RoleSlice::role)
                .containsExactly(AssetRole.GROWTH, AssetRole.IDLE);
    }

    @Test
    void cashflowAssetRatio는_roleAllocation의_CASHFLOW_ratio와_항상_같다() {
        // 33.33%씩 3등분 — 독립 반올림이면 헤드라인(33)과 도넛(34)이 어긋나는 케이스.
        AssetBreakdown breakdown = breakdown(10_000_000, 0, 10_000_000, 0, 10_000_000);

        int cashflowSlice = breakdown.roleAllocation().stream()
                .filter(s -> s.role() == AssetRole.CASHFLOW)
                .mapToInt(RoleSlice::ratio)
                .findFirst().orElseThrow();

        assertThat(breakdown.cashflowAssetRatio()).isEqualTo(cashflowSlice);
        assertThat(breakdown.cashflowAssetRatio()).isEqualTo(34);
    }

    @Test
    void roleAllocation은_금액0역할_제외_선언순서유지_비율합100() {
        // 현금흐름 3천 / 성장 3천 / 잠자는 돈 2천(현금3천−연금예수1천) / 연금 2천(연금예수1천+연금종목1천) = 1억
        AssetBreakdown breakdown = breakdown(30_000_000, 10_000_000, 30_000_000, 10_000_000, 30_000_000);

        assertThat(breakdown.roleAllocation()).extracting(RoleSlice::role)
                .containsExactly(AssetRole.CASHFLOW, AssetRole.GROWTH, AssetRole.IDLE, AssetRole.PENSION);
        assertThat(breakdown.roleAllocation()).extracting(RoleSlice::ratio)
                .containsExactly(30, 30, 20, 20);
        assertThat(breakdown.roleAllocation().stream().mapToInt(RoleSlice::ratio).sum()).isEqualTo(100);
    }

    private AssetBreakdown breakdown(long cash, long pensionCash, long nonStock,
                                     long pensionHolding, long stock) {
        return new AssetBreakdown(won(cash), won(pensionCash), won(nonStock),
                won(pensionHolding), won(stock));
    }

    private BigDecimal won(long value) {
        return BigDecimal.valueOf(value);
    }
}
