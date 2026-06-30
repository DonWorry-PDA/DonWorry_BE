package com.sol.user.asset.service;

import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.InvestmentCheckResponse;
import com.sol.user.asset.dto.InvestmentCheckResponse.GrowthAsset;
import com.sol.user.asset.dto.InvestmentCheckResponse.RoleContribution;
import com.sol.user.asset.dto.InvestmentCheckResponse.UncoveredCashflow;
import com.sol.user.asset.mapper.AssetMapper;
import com.sol.user.asset.service.AssetAggregator.AssetSnapshot;
import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.dto.StockDividendProjection;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 투자 건강검진(#2) 상세 산출. 자산을 4역할(현금흐름·성장·잠자는 돈·연금)로 분해하고, 개별주 성장 블록을 만든다.
 *
 * <p>금액 단일 진실원천은 {@link AssetAggregator}({@link AssetBreakdown})다. 역할 4분류·비율은
 * {@link AssetBreakdown#roleAllocation()}에서 그대로 받고, 여기서는 라벨·설명과 <b>실배당 기반 월 현금흐름</b>만 붙인다.
 *
 * <p>월 현금흐름은 추정치가 아니라 보유분 실데이터다: 현금흐름 역할은 보유 ETF의 실분배
 * ({@link HoldingRepository#findDividendCalendarInputsByUserId}), 성장 역할은 개별주의 종목별 시가배당률
 * ({@link HoldingRepository#findStockDividendsByUserId})로 산출한다. ETF 실분배 데이터가 전혀 없을 때만
 * 대표배당률({@link PortfolioConstants#REPRESENTATIVE_DIVIDEND_RATE})로 폴백한다(풀 밖 종목 방어).
 *
 * <p>모든 월 현금흐름은 <b>세전(gross)</b>이다 — 홈·월간리포트와 동일 기준으로 화면 간 정합한다.
 * 실분배·개별주배당·폴백추정 전 경로 모두 원천징수 차감 없이 반환한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentCheckService {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    /** 연 시가배당률(%)을 월 배당액으로: eval × (yield/100) / 12 = eval × yield / 1200. */
    private static final BigDecimal PERCENT_MONTHS = BigDecimal.valueOf(1200);

    // 종목 쏠림 임계값 — 최대 단일종목 비중(%) 기준. (분산투자 통념: 단일종목 40% 미만 양호)
    private static final int CONCENTRATION_LOW_MAX = 40;   // < 40 → 낮음
    private static final int CONCENTRATION_MID_MAX = 70;   // < 70 → 보통, 이상 → 높음

    /** 55세 인출제약 연금계좌 — 현금흐름 역할 분류 기준은 {@link AssetAggregator}와 동일(비STOCK·비연금). */
    private static final Set<String> PENSION_ACCOUNT_TYPES = Set.of("PENSION_SAVING", "IRP");
    private static final String STOCK_PRODUCT_TYPE = "STOCK";

    /** 성장 자산을 배당형으로 옮기면 현금흐름이 늘어나는 경우(저배당 성장주 위주). */
    private static final String SUGGESTION_MOVE =
            "직접 투자 중인 개별주는 대부분 시세차익을 노려요. 일부를 배당 중심으로 옮기면 매달 현금흐름이 더 생겨요.";
    /** 이미 종목 배당이 배당형 ETF 수준 이상이라 옮기면 오히려 손해인 경우(고배당 가치주 위주). */
    private static final String SUGGESTION_KEEP =
            "직접 투자 중인 개별주에서 이미 배당이 꾸준히 나와요. 배당 중심으로 옮기면 현금흐름이 오히려 줄 수 있어요.";

    private final AssetAggregator assetAggregator;
    private final HoldingRepository holdingRepository;
    private final AssetMapper assetMapper;
    private final ProductBatchClient productBatchClient;

    public InvestmentCheckResponse check(Long userId) {
        // 분배 공백 경고(#193)는 종목별 평가액·productId가 필요해 snapshot(보유·상품 원본 포함)을 쓴다.
        AssetSnapshot snapshot = assetAggregator.aggregateSnapshot(userId);
        AssetBreakdown breakdown = snapshot.breakdown();

        List<StockDividendProjection> rawStocks = holdingRepository.findStockDividendsByUserId(userId);
        List<HoldingDividendCalendarProjection> etfDividends =
                holdingRepository.findDividendCalendarInputsByUserId(userId);

        // quantity × 실시간 현재가 → 종목별 평가액 산출
        List<EnrichedStock> stocks = enrichWithPrices(rawStocks);

        BigDecimal cashflowMonthly = cashflowMonthlyDividend(breakdown, etfDividends);
        BigDecimal stockMonthly = stockMonthlyDividend(stocks);

        Integer cashflowRatio = breakdown.cashflowAssetRatio();
        return InvestmentCheckResponse.builder()
                // 헤드라인은 도넛 조각(CASHFLOW 역할 ratio)과 동일 출처라 항상 일치한다. 자산 없으면 0.
                .cashflowAssetRatio(cashflowRatio == null ? 0 : cashflowRatio)
                .totalAsset(breakdown.grossTotal())
                .roles(assetMapper.toRoleContributions(breakdown, cashflowMonthly, stockMonthly))
                .growthAsset(buildGrowthAsset(breakdown, stocks, stockMonthly))
                .uncoveredCashflow(uncoveredCashflow(snapshot, etfDividends))
                .build();
    }

    /** quantity × 현재가 조회로 평가액을 산출한 enriched 레코드. */
    private record EnrichedStock(
            Long productId,
            String productName,
            BigDecimal evaluationAmount,
            BigDecimal dividendYield,
            String sector
    ) {}

    private List<EnrichedStock> enrichWithPrices(List<StockDividendProjection> rawStocks) {
        if (rawStocks.isEmpty()) return List.of();
        List<Long> ids = rawStocks.stream().map(StockDividendProjection::getProductId).toList();
        Map<Long, Long> prices = productBatchClient.fetchStockPrices(ids);
        return rawStocks.stream()
                .map(s -> {
                    long price = prices.getOrDefault(s.getProductId(), 0L);
                    BigDecimal qty = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
                    BigDecimal eval = qty.multiply(BigDecimal.valueOf(price));
                    return new EnrichedStock(s.getProductId(), s.getProductName(), eval,
                            s.getDividendYield(), s.getSector());
                })
                .toList();
    }

    /**
     * 개별주 성장 블록. 개별주 보유가 없으면 null. 현재 배당(종목 실배당) vs 배당형 ETF로 옮겼을 때의 배당을
     * 비교해, 옮기는 게 이득이면 이동 유도, 손해면 보유 유지 멘트로 분기한다.
     */
    private GrowthAsset buildGrowthAsset(AssetBreakdown breakdown,
                                         List<EnrichedStock> stocks, BigDecimal stockMonthly) {
        BigDecimal stockTotal = breakdown.stockHoldingValue();
        if (stockTotal.signum() <= 0) {
            return null;
        }

        EnrichedStock top = stocks.stream()
                .max(Comparator.comparing(s -> nz(s.evaluationAmount())))
                .orElse(null);
        int concentration = top == null ? 0 : percent(top.evaluationAmount(), stockTotal);

        // 섹터 쏠림 — 단일종목 쏠림이 낮아도(예: 삼성전자·SK하이닉스·삼성SDI 분산) 같은 섹터면 위험은 집중.
        Map.Entry<String, BigDecimal> topSector = topSectorByValue(stocks);
        int sectorConcentration = topSector == null ? 0 : percent(topSector.getValue(), stockTotal);

        // 전액 배당형 ETF로 옮겼을 때의 월 배당(대표배당률, gross) vs 현재 종목 실배당(gross).
        BigDecimal converted = monthlyByRate(stockTotal, PortfolioConstants.REPRESENTATIVE_DIVIDEND_RATE);
        BigDecimal delta = converted.subtract(stockMonthly);

        return GrowthAsset.builder()
                .amount(stockTotal)
                .topStockName(top == null ? null : top.productName())
                .concentrationRatio(concentration)
                .concentrationLevel(concentrationLevel(concentration))
                .topSector(topSector == null ? null : topSector.getKey())
                .sectorConcentrationRatio(sectorConcentration)
                .sectorConcentrationLevel(concentrationLevel(sectorConcentration))
                .currentMonthlyDividend(stockMonthly)
                .convertedMonthlyDividend(converted)
                .deltaMonthlyDividend(delta)
                .suggestion(delta.signum() > 0 ? SUGGESTION_MOVE : SUGGESTION_KEEP)
                .build();
    }

    /**
     * 현금흐름 역할의 월 배당(gross) = 보유 ETF 실분배 합(종목별 수량 × 분배/주 ÷ 배당주기).
     * 실분배 데이터가 전혀 없으면(풀 밖 종목 등) 현금흐름 자산 전체를 대표배당률로 추정 폴백.
     */
    private BigDecimal cashflowMonthlyDividend(AssetBreakdown breakdown,
                                              List<HoldingDividendCalendarProjection> etfDividends) {
        if (etfDividends.isEmpty()) {
            return monthlyByRate(breakdown.nonStockHoldingValue(), PortfolioConstants.REPRESENTATIVE_DIVIDEND_RATE);
        }
        return etfDividends.stream()
                .map(this::monthlyFromDistribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** ETF 1종목 월 분배액 = 수량 × 분배/주 ÷ 배당주기(월). 주기는 쿼리에서 >0 보장. */
    private BigDecimal monthlyFromDistribution(HoldingDividendCalendarProjection p) {
        Integer interval = p.getDistributionIntervalMonths();
        if (interval == null || interval <= 0) {
            return BigDecimal.ZERO;
        }
        return nz(p.getQuantity()).multiply(nz(p.getAmountPerUnit()))
                .divide(BigDecimal.valueOf(interval), 0, RoundingMode.HALF_UP);
    }

    /** 섹터별 평가액 합 중 최대 항목. 섹터 미적재(NULL/공백) 종목은 제외. 없으면 null. */
    private Map.Entry<String, BigDecimal> topSectorByValue(List<EnrichedStock> stocks) {
        Map<String, BigDecimal> bySector = new HashMap<>();
        for (EnrichedStock s : stocks) {
            String sector = s.sector();
            if (sector == null || sector.isBlank()) {
                continue;
            }
            bySector.merge(sector, nz(s.evaluationAmount()), BigDecimal::add);
        }
        return bySector.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
    }

    /** 성장 역할의 월 배당(gross) = 개별주 종목별 (평가액 × 시가배당률 / 1200) 합. */
    private BigDecimal stockMonthlyDividend(List<EnrichedStock> stocks) {
        return stocks.stream()
                .map(s -> nz(s.evaluationAmount()).multiply(nz(s.dividendYield()))
                        .divide(PERCENT_MONTHS, 0, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String concentrationLevel(int concentration) {
        if (concentration < CONCENTRATION_LOW_MAX) {
            return "낮음";
        }
        if (concentration < CONCENTRATION_MID_MAX) {
            return "보통";
        }
        return "높음";
    }

    /**
     * 분배 데이터 공백 경고(#193) — "재료>0인데 분배금 0"인 현금흐름 자산 집계.
     * 현금흐름 역할 보유(비STOCK·비연금) 중 실분배 데이터({@code etfDividends})에 productId가 없는 종목이 대상이다.
     * 0192S0(분배 없음)·직접보유 채권/펀드처럼 분배가 없는 종목을 추정하지 않고(거짓 과대 방지) 현황으로만 알린다.
     *
     * <p>실분배 데이터가 전무하면(전체 폴백추정 경로) 신호가 없어 경고 대상이 아니다 — null.
     * 일부라도 실분배가 잡힌 경우, 그 신호에 빠진 보유만 "분배 없음"으로 본다.
     */
    private UncoveredCashflow uncoveredCashflow(AssetSnapshot snapshot,
                                               List<HoldingDividendCalendarProjection> etfDividends) {
        if (etfDividends.isEmpty()) {
            return null;
        }
        Set<Long> covered = etfDividends.stream()
                .map(HoldingDividendCalendarProjection::getProductId)
                .collect(Collectors.toSet());

        BigDecimal amount = BigDecimal.ZERO;
        // amount는 보유 row별로 합산하되, 종목명은 productId 기준으로 dedupe(한 종목 다계좌 보유 시 1회만 노출).
        Map<Long, String> namesByProduct = new LinkedHashMap<>();
        for (HoldingWithProduct holding : snapshot.holdings()) {
            if (!isCashflowHolding(holding, snapshot.products()) || covered.contains(holding.getProductId())) {
                continue;
            }
            amount = amount.add(nz(holding.getEvaluationAmount()));
            ProductBatchItem product = snapshot.products().get(holding.getProductId());
            namesByProduct.putIfAbsent(holding.getProductId(),
                    product == null ? String.valueOf(holding.getProductId()) : product.productName());
        }
        if (amount.signum() <= 0) {
            return null;
        }
        return UncoveredCashflow.builder()
                .amount(amount)
                .productNames(new ArrayList<>(namesByProduct.values()))
                .build();
    }

    /** 현금흐름 역할 보유 = 비STOCK · 비연금계좌. {@link AssetAggregator}의 nonStockHoldingValue 분류와 동일. */
    private boolean isCashflowHolding(HoldingWithProduct holding, Map<Long, ProductBatchItem> products) {
        ProductBatchItem product = products.get(holding.getProductId());
        boolean stock = product != null && STOCK_PRODUCT_TYPE.equals(product.productType());
        boolean pension = PENSION_ACCOUNT_TYPES.contains(holding.getAccountType());
        return !stock && !pension;
    }

    /** 연 배당률(분수) 기준 월 배당액(원, 정수). */
    private BigDecimal monthlyByRate(BigDecimal amount, BigDecimal annualRate) {
        return nz(amount).multiply(annualRate)
                .divide(MONTHS_PER_YEAR, 0, RoundingMode.HALF_UP);
    }

    /** numerator/denominator 정수 %. denominator 0 이면 0. */
    private int percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return 0;
        }
        return nz(numerator).multiply(BigDecimal.valueOf(100))
                .divide(denominator, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
