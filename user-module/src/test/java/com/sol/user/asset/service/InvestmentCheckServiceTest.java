package com.sol.user.asset.service;

import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.InvestmentCheckResponse;
import com.sol.user.asset.dto.InvestmentCheckResponse.GrowthAsset;
import com.sol.user.asset.dto.InvestmentCheckResponse.RoleContribution;
import com.sol.user.asset.service.AssetAggregator.AssetSnapshot;
import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.dto.StockDividendProjection;
import com.sol.user.asset.mapper.AssetMapper;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentCheckServiceTest {

    private static final Long USER_ID = 1L;

    @Mock AssetAggregator assetAggregator;
    @Mock HoldingRepository holdingRepository;
    @Mock ProductBatchClient productBatchClient;
    @Spy AssetMapper assetMapper = new AssetMapper();

    @InjectMocks InvestmentCheckService service;

    @BeforeEach
    void setupStockPriceMock() {
        // stock()은 quantity=eval로 설정, 현재가=1로 고정 → evaluationAmount=quantity×1=eval.
        // lenient: 주식 없는 테스트에서 이 stubbing이 호출되지 않아도 UnnecessaryStubbingException 미발생.
        lenient().when(productBatchClient.fetchStockPrices(anyList()))
                .thenAnswer(inv -> {
                    List<Long> ids = inv.getArgument(0);
                    return ids.stream().collect(Collectors.toMap(id -> id, id -> 1L));
                });
    }

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
        // 100주×300원/월 + 50주×600원÷3개월 = 30,000 + 10,000 = 40,000원/월(gross)
        when(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .thenReturn(List.of(etf(101L, 100, "300", 1), etf(102L, 50, "600", 3)));

        InvestmentCheckResponse response = service.check(USER_ID);

        // 실분배가 있으므로 폴백(3천만×3.5%/12)이 아니라 실값. net = 40,000 × (1−0.154) = 33,840.
        assertThat(role(response, "CASHFLOW").monthlyCashflow()).isEqualByComparingTo("33840");
    }

    @Test
    void 현금흐름_ETF실분배_데이터가_없으면_대표배당률_폴백() {
        stubBreakdown(new AssetBreakdown(
                won(20_000_000), BigDecimal.ZERO, won(30_000_000), BigDecimal.ZERO, BigDecimal.ZERO));
        // findDividendCalendarInputsByUserId 미스텁 → 빈 리스트 → 폴백

        InvestmentCheckResponse response = service.check(USER_ID);

        // 3천만 × 0.035 / 12 = 87,500(gross) → net = 87,500 × 0.846 = 74,025
        assertThat(role(response, "CASHFLOW").monthlyCashflow()).isEqualByComparingTo("74025");
    }

    @Test
    void 성장_월배당은_개별주_실배당으로_계산된다() {
        stubBreakdown(stockOnlyBreakdown(10_000_000));
        // 1천만 × 6.00% / 12 = 50,000원/월(gross) → net = 50,000 × 0.846 = 42,300
        when(holdingRepository.findStockDividendsByUserId(USER_ID))
                .thenReturn(List.of(stock(201L, "현대차", 10_000_000, "6.00")));

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(role(response, "GROWTH").monthlyCashflow()).isEqualByComparingTo("42300");
        assertThat(response.growthAsset().currentMonthlyDividend()).isEqualByComparingTo("42300");
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
        // gross 8,333(1천만×1%/12) → net 7,050. 배당ETF 전환 gross 29,167 → net 24,675 → delta +양수
        when(holdingRepository.findStockDividendsByUserId(USER_ID))
                .thenReturn(List.of(stock(1L, "삼성바이오로직스", 10_000_000, "1.00")));

        GrowthAsset growth = service.check(USER_ID).growthAsset();

        assertThat(growth.currentMonthlyDividend()).isEqualByComparingTo("7050");   // 8333×0.846=7049.7→7050
        assertThat(growth.convertedMonthlyDividend()).isEqualByComparingTo("24675"); // 29167×0.846=24675.3→24675
        assertThat(growth.deltaMonthlyDividend()).isEqualByComparingTo("17625");      // 24675−7050
        assertThat(growth.suggestion()).contains("옮기면").contains("더");
    }

    @Test
    void 고배당주는_옮기면_손해_멘트로_플립() {
        stubBreakdown(stockOnlyBreakdown(10_000_000));
        // gross 58,333(7%) → net 49,350 > 배당ETF 전환 net 24,675 → delta 음수
        when(holdingRepository.findStockDividendsByUserId(USER_ID))
                .thenReturn(List.of(stock(1L, "현대해상", 10_000_000, "7.00")));

        GrowthAsset growth = service.check(USER_ID).growthAsset();

        assertThat(growth.deltaMonthlyDividend()).isEqualByComparingTo("-24675"); // 24675−49350
        assertThat(growth.suggestion()).contains("오히려 줄");
    }

    // ── #193 분배 공백 경고 ──────────────────────────────────────────────────────

    @Test
    void 분배데이터가_있고_공백종목이_있으면_경고에_금액과_종목명이_담긴다() {
        // 현금흐름 자산 5천 = 분배되는 ETF(101) 3천 + 분배없는 채권혼합(999) 2천
        AssetBreakdown breakdown = new AssetBreakdown(
                BigDecimal.ZERO, BigDecimal.ZERO, won(50_000_000), BigDecimal.ZERO, BigDecimal.ZERO);
        stubSnapshot(breakdown,
                List.of(holding(1L, 101L, 30_000_000, "BROKERAGE"),
                        holding(2L, 999L, 20_000_000, "BROKERAGE")),
                Map.of(101L, product(101L, "SOL 국고채3년", "ETF"),
                        999L, product(999L, "SOL 코스피200채권혼합50", "FUND")));
        // 101만 실분배 데이터 보유 → 999는 "재료>0 분배금 0"
        when(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .thenReturn(List.of(etf(101L, 100, "300", 1)));

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(response.uncoveredCashflow()).isNotNull();
        assertThat(response.uncoveredCashflow().amount()).isEqualByComparingTo("20000000");
        assertThat(response.uncoveredCashflow().productNames()).containsExactly("SOL 코스피200채권혼합50");
    }

    @Test
    void 분배데이터가_전무하면_폴백추정이라_경고는_null() {
        AssetBreakdown breakdown = new AssetBreakdown(
                BigDecimal.ZERO, BigDecimal.ZERO, won(20_000_000), BigDecimal.ZERO, BigDecimal.ZERO);
        stubSnapshot(breakdown,
                List.of(holding(1L, 999L, 20_000_000, "BROKERAGE")),
                Map.of(999L, product(999L, "SOL 코스피200채권혼합50", "FUND")));
        // findDividendCalendarInputsByUserId 미스텁 → 빈 리스트 → 전체 폴백추정(경고 아님)

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(response.uncoveredCashflow()).isNull();
    }

    @Test
    void 개별주와_연금보유는_현금흐름_공백경고_대상이_아니다() {
        AssetBreakdown breakdown = new AssetBreakdown(
                BigDecimal.ZERO, BigDecimal.ZERO, won(30_000_000), won(10_000_000), won(20_000_000));
        stubSnapshot(breakdown,
                List.of(holding(1L, 101L, 30_000_000, "BROKERAGE"),   // 커버됨
                        holding(2L, 500L, 20_000_000, "BROKERAGE"),   // STOCK → 제외
                        holding(3L, 600L, 10_000_000, "IRP")),        // 연금계좌 → 제외
                Map.of(101L, product(101L, "SOL 국고채3년", "ETF"),
                        500L, product(500L, "삼성전자", "STOCK"),
                        600L, product(600L, "SOL 미국S&P500", "ETF")));
        when(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .thenReturn(List.of(etf(101L, 100, "300", 1)));

        InvestmentCheckResponse response = service.check(USER_ID);

        // 비커버 현금흐름 자산이 없음(주식·연금은 분류상 제외) → 경고 null
        assertThat(response.uncoveredCashflow()).isNull();
    }

    @Test
    void 같은_공백종목을_여러계좌로_보유하면_금액은_합산되고_종목명은_한_번만() {
        // 분배없는 채권혼합(999)을 두 계좌에 각 2천·1천 보유 → amount 3천 합산, 종목명은 1회
        AssetBreakdown breakdown = new AssetBreakdown(
                BigDecimal.ZERO, BigDecimal.ZERO, won(60_000_000), BigDecimal.ZERO, BigDecimal.ZERO);
        stubSnapshot(breakdown,
                List.of(holding(1L, 101L, 30_000_000, "BROKERAGE"),
                        holding(2L, 999L, 20_000_000, "BROKERAGE"),
                        holding(3L, 999L, 10_000_000, "CMA")),
                Map.of(101L, product(101L, "SOL 국고채3년", "ETF"),
                        999L, product(999L, "SOL 코스피200채권혼합50", "FUND")));
        when(holdingRepository.findDividendCalendarInputsByUserId(USER_ID))
                .thenReturn(List.of(etf(101L, 100, "300", 1)));

        InvestmentCheckResponse response = service.check(USER_ID);

        assertThat(response.uncoveredCashflow().amount()).isEqualByComparingTo("30000000");
        assertThat(response.uncoveredCashflow().productNames()).containsExactly("SOL 코스피200채권혼합50");
    }

    // ── helpers ──

    private void stubBreakdown(AssetBreakdown breakdown) {
        when(assetAggregator.aggregateSnapshot(USER_ID))
                .thenReturn(new AssetSnapshot(breakdown, List.of(), List.of(), Map.of()));
    }

    private void stubSnapshot(AssetBreakdown breakdown, List<HoldingWithProduct> holdings,
                             Map<Long, ProductBatchItem> products) {
        when(assetAggregator.aggregateSnapshot(USER_ID))
                .thenReturn(new AssetSnapshot(breakdown, List.of(), holdings, products));
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
            // quantity=eval, 현재가=1(setupStockPriceMock) → evaluationAmount = eval×1 = eval
            public BigDecimal getQuantity() { return BigDecimal.valueOf(eval); }
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

    private HoldingWithProduct holding(long holdingId, long productId, long eval, String accountType) {
        return new HoldingWithProduct() {
            public Long getHoldingId() { return holdingId; }
            public Long getAccountId() { return holdingId; }
            public Long getProductId() { return productId; }
            public BigDecimal getEvaluationAmount() { return BigDecimal.valueOf(eval); }
            public BigDecimal getQuantity() { return null; }
            public String getAccountType() { return accountType; }
        };
    }

    private ProductBatchItem product(long productId, String name, String type) {
        return new ProductBatchItem(productId, name, type, null);
    }

    private BigDecimal won(long value) {
        return BigDecimal.valueOf(value);
    }
}
