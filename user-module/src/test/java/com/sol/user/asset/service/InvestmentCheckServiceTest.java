package com.sol.user.asset.service;

import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.InvestmentCheckResponse;
import com.sol.user.asset.dto.InvestmentCheckResponse.GrowthAsset;
import com.sol.user.asset.dto.InvestmentCheckResponse.RoleContribution;
import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.dto.StockDividendProjection;
import com.sol.user.holding.repository.HoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentCheckServiceTest {

    private static final Long USER_ID = 1L;

    @Mock AssetAggregator assetAggregator;
    @Mock HoldingRepository holdingRepository;

    @InjectMocks InvestmentCheckService service;

    @Test
    void 자산이_없으면_비율0_역할빈리스트_성장블록null() {
        stubBreakdown(zeroBreakdown());

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(response.cashflowAssetRatio()).isZero();
        assertThat(response.totalAsset()).isEqualByComparingTo("0");
        assertThat(response.roles()).isEmpty();
        assertThat(response.growthAsset()).isNull();
    }

    @Test
    void 자산을_4역할로_분해하고_비율합은_100() {
        // 현금 3천(연금예수 1천 포함→잠자는 2천) / 현금흐름 3천 / 연금종목 1천 / 주식 3천 = 순자산 1억
        stubBreakdown(new AssetBreakdown(
                won(30_000_000), won(10_000_000), won(30_000_000), won(10_000_000), won(30_000_000)));

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(response.cashflowAssetRatio()).isEqualTo(30); // 현금흐름 3천 / 순자산 1억
        assertThat(response.totalAsset()).isEqualByComparingTo("100000000");
        assertThat(response.roles()).extracting(RoleContribution::role)
                .containsExactly("CASHFLOW", "GROWTH", "IDLE", "PENSION");
        assertThat(response.roles()).extracting(RoleContribution::ratio)
                .containsExactly(30, 30, 20, 20);
        assertThat(response.roles().stream().mapToInt(RoleContribution::ratio).sum()).isEqualTo(100);
    }

    @Test
    void 헤드라인비율은_CASHFLOW_역할_비율과_동일하고_비정수여도_합은_100() {
        // 현금흐름·잠자는 돈·성장 각 1천만 = 33.33%씩. 내림 33+33+33=99, 잔여 1은 소수부 동률→
        // 선언 순서상 CASHFLOW가 먼저 +1 → CASHFLOW 34. 독립 HALF_UP(33)이면 헤드라인이 도넛과 어긋난다.
        stubBreakdown(new AssetBreakdown(
                won(10_000_000), BigDecimal.ZERO, won(10_000_000), BigDecimal.ZERO, won(10_000_000)));

        InvestmentCheckResponse response = service.check(USER_ID);

        int cashflowRoleRatio = role(response, "CASHFLOW").ratio();
        assertThat(response.cashflowAssetRatio()).isEqualTo(cashflowRoleRatio);
        assertThat(response.cashflowAssetRatio()).isEqualTo(34);
        assertThat(response.roles().stream().mapToInt(RoleContribution::ratio).sum()).isEqualTo(100);
    }

    @Test
    void 금액이_0인_역할은_분해에서_제외된다() {
        // 잠자는 5천 / 성장 5천만. 현금흐름·연금 0 → 두 역할만.
        stubBreakdown(new AssetBreakdown(
                won(50_000_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, won(50_000_000)));

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(response.cashflowAssetRatio()).isZero();
        assertThat(response.roles()).extracting(RoleContribution::role)
                .containsExactly("GROWTH", "IDLE");
    }

    @Test
    void 현금흐름_월배당은_보유ETF_실분배로_계산된다() {
        stubBreakdown(new AssetBreakdown(
                won(20_000_000), BigDecimal.ZERO, won(30_000_000), BigDecimal.ZERO, BigDecimal.ZERO));
        // 100주×300원/월 + 50주×600원÷3개월 = 30,000 + 10,000 = 40,000원/월
        when(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .thenReturn(List.of(etf(101L, 100, "300", 1), etf(102L, 50, "600", 3)));

        InvestmentCheckResponse response = service.check(USER_ID);

        // 실분배가 있으므로 대표배당률 폴백(3천만×3.5%/12=87,500)이 아니라 실값 40,000.
        assertThat(role(response, "CASHFLOW").monthlyCashflow()).isEqualByComparingTo("40000");
    }

    @Test
    void 현금흐름_ETF실분배_데이터가_없으면_대표배당률_폴백() {
        stubBreakdown(new AssetBreakdown(
                won(20_000_000), BigDecimal.ZERO, won(30_000_000), BigDecimal.ZERO, BigDecimal.ZERO));
        // findDividendCalendarInputsByUserId 미스텁 → 빈 리스트 → 폴백

        InvestmentCheckResponse response = service.check(USER_ID);

        // 3천만 × 0.035 / 12 = 87,500
        assertThat(role(response, "CASHFLOW").monthlyCashflow()).isEqualByComparingTo("87500");
    }

    @Test
    void 성장_월배당은_개별주_실배당으로_계산된다() {
        stubBreakdown(stockOnlyBreakdown(10_000_000));
        // 1천만 × 6.00% / 12 = 50,000원/월
        when(holdingRepository.findStockDividendsByUserId(USER_ID))
                .thenReturn(List.of(stock(201L, "현대차", 10_000_000, "6.00")));

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(role(response, "GROWTH").monthlyCashflow()).isEqualByComparingTo("50000");
        assertThat(response.growthAsset().currentMonthlyDividend()).isEqualByComparingTo("50000");
    }

    @Test
    void 성장블록_종목쏠림_최대비중과_레벨을_계산한다() {
        // 삼성 1.2천 / SK 1천 / 현대 0.9천 / LG 0.9천 = 4천, 최대 30% → 낮음
        stubBreakdown(stockOnlyBreakdown(40_000_000));
        when(holdingRepository.findStockDividendsByUserId(USER_ID)).thenReturn(List.of(
                stock(1L, "삼성전자", 12_000_000, "0"), stock(2L, "SK하이닉스", 10_000_000, "0"),
                stock(3L, "현대차", 9_000_000, "0"), stock(4L, "LG에너지솔루션", 9_000_000, "0")));

        GrowthAsset growth = service.check(USER_ID).growthAsset();

        assertThat(growth.amount()).isEqualByComparingTo("40000000");
        assertThat(growth.topStockName()).isEqualTo("삼성전자");
        assertThat(growth.concentrationRatio()).isEqualTo(30);
        assertThat(growth.concentrationLevel()).isEqualTo("낮음");
    }

    @Test
    void 섹터쏠림은_단일종목쏠림이_낮아도_높게_잡힐_수_있다() {
        // 삼성전자 1.2천(전기·전자)/SK하이닉스 1천(전기·전자)/현대차 0.9천(운송장비)/LG엔솔 0.9천(전기·전자) = 4천
        // 단일종목 최대 30%(낮음)지만 전기·전자 섹터 합 3.1천 = 78%(높음)
        stubBreakdown(stockOnlyBreakdown(40_000_000));
        when(holdingRepository.findStockDividendsByUserId(USER_ID)).thenReturn(List.of(
                stock(1L, "삼성전자", 12_000_000, "0", "전기·전자"),
                stock(2L, "SK하이닉스", 10_000_000, "0", "전기·전자"),
                stock(3L, "현대차", 9_000_000, "0", "운송장비"),
                stock(4L, "LG에너지솔루션", 9_000_000, "0", "전기·전자")));

        GrowthAsset growth = service.check(USER_ID).growthAsset();

        assertThat(growth.concentrationRatio()).isEqualTo(30);
        assertThat(growth.concentrationLevel()).isEqualTo("낮음");
        assertThat(growth.topSector()).isEqualTo("전기·전자");
        assertThat(growth.sectorConcentrationRatio()).isEqualTo(78);
        assertThat(growth.sectorConcentrationLevel()).isEqualTo("높음");
    }

    @Test
    void 단일종목만_보유하면_쏠림_높음() {
        stubBreakdown(stockOnlyBreakdown(30_000_000));
        when(holdingRepository.findStockDividendsByUserId(USER_ID))
                .thenReturn(List.of(stock(1L, "삼성전자", 30_000_000, "0")));

        GrowthAsset growth = service.check(USER_ID).growthAsset();

        assertThat(growth.concentrationRatio()).isEqualTo(100);
        assertThat(growth.concentrationLevel()).isEqualTo("높음");
    }

    @Test
    void 저배당_성장주는_배당ETF로_옮기면_이득_멘트() {
        stubBreakdown(stockOnlyBreakdown(10_000_000));
        // 1.00% → 월 8,333. 배당ETF 전환 시 1천만×3.5%/12=29,167 → delta +양수
        when(holdingRepository.findStockDividendsByUserId(USER_ID))
                .thenReturn(List.of(stock(1L, "삼성바이오로직스", 10_000_000, "1.00")));

        GrowthAsset growth = service.check(USER_ID).growthAsset();

        assertThat(growth.currentMonthlyDividend()).isEqualByComparingTo("8333");
        assertThat(growth.convertedMonthlyDividend()).isEqualByComparingTo("29167");
        assertThat(growth.deltaMonthlyDividend()).isEqualByComparingTo("20834");
        assertThat(growth.suggestion()).contains("옮기면").contains("더");
    }

    @Test
    void 고배당주는_옮기면_손해_멘트로_플립() {
        stubBreakdown(stockOnlyBreakdown(10_000_000));
        // 7.00% → 월 58,333 > 배당ETF 전환 29,167 → delta 음수
        when(holdingRepository.findStockDividendsByUserId(USER_ID))
                .thenReturn(List.of(stock(1L, "현대해상", 10_000_000, "7.00")));

        GrowthAsset growth = service.check(USER_ID).growthAsset();

        assertThat(growth.deltaMonthlyDividend()).isEqualByComparingTo("-29166");
        assertThat(growth.suggestion()).contains("오히려 줄");
    }

    // ── helpers ──

    private void stubBreakdown(AssetBreakdown breakdown) {
        when(assetAggregator.aggregate(USER_ID)).thenReturn(breakdown);
    }

    private RoleContribution role(InvestmentCheckResponse response, String role) {
        return response.roles().stream().filter(r -> r.role().equals(role)).findFirst().orElseThrow();
    }

    private AssetBreakdown zeroBreakdown() {
        return new AssetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private AssetBreakdown stockOnlyBreakdown(long stock) {
        return new AssetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, won(stock));
    }

    private StockDividendProjection stock(long productId, String name, long eval, String yield) {
        return stock(productId, name, eval, yield, "기타");
    }

    private StockDividendProjection stock(long productId, String name, long eval, String yield, String sector) {
        return new StockDividendProjection() {
            public Long getProductId() { return productId; }
            public String getProductName() { return name; }
            public BigDecimal getEvaluationAmount() { return BigDecimal.valueOf(eval); }
            public BigDecimal getDividendYield() { return new BigDecimal(yield); }
            public String getSector() { return sector; }
        };
    }

    private HoldingDividendCalendarProjection etf(long productId, long quantity,
                                                 String amountPerUnit, int intervalMonths) {
        return new HoldingDividendCalendarProjection() {
            public Long getProductId() { return productId; }
            public String getProductName() { return "ETF" + productId; }
            public BigDecimal getQuantity() { return BigDecimal.valueOf(quantity); }
            public BigDecimal getAmountPerUnit() { return new BigDecimal(amountPerUnit); }
            public LocalDate getLatestPaymentDate() { return null; }
            public Integer getDistributionIntervalMonths() { return intervalMonths; }
        };
    }

    private BigDecimal won(long value) {
        return BigDecimal.valueOf(value);
    }
}
