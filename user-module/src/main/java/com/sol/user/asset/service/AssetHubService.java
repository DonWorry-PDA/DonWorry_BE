package com.sol.user.asset.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetHubMenus;
import com.sol.user.asset.dto.AssetHubResponse;
import com.sol.user.asset.mapper.AssetMapper;
import com.sol.user.asset.type.AssetCategory;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.entity.SalaryPlan;
import com.sol.user.monthlysalary.repository.SalaryPlanRepository;
import com.sol.user.monthlysalary.service.CashFlowDiagnosisService;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import com.sol.user.stability.dto.LifeStabilityResponse;
import com.sol.user.stability.service.LifeStabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetHubService {

    private static final String FLOW_INCOME = "INCOME";
    private static final String FLOW_EXPENSE = "EXPENSE";

    private final CashFlowEventRepository cashFlowEventRepository;
    private final CashFlowDiagnosisService cashFlowDiagnosisService;
    private final LifeStabilityService lifeStabilityService;
    private final AssetAggregator assetAggregator;
    private final SalaryPlanRepository salaryPlanRepository;
    private final AssetMapper assetMapper;

    public AssetHubResponse getHub(Long userId) {
        AssetAggregator.AssetSnapshot snapshot = assetAggregator.aggregateSnapshot(userId);
        Map<AssetCategory, BigDecimal> byCategory = aggregateByCategory(snapshot);
        BigDecimal totalAsset = byCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth thisMonth = YearMonth.now();
        LocalDate start = thisMonth.atDay(1);
        LocalDate end = thisMonth.atEndOfMonth();
        AssetMapper.EtfSnapshot etfSnapshot = assetMapper.toEtfSnapshot(snapshot);
        AssetMapper.StockSnapshot stockSnapshot = assetMapper.toStockSnapshot(snapshot);

        return AssetHubResponse.builder()
                .totalAsset(totalAsset)
                // 월간 자산 스냅샷(#6) 도입 전까지 증감 계산 불가 → null / FLAT
                .changeAmount(null)
                .changeDirection("FLAT")
                .allocation(assetMapper.toCategoryAllocation(byCategory, totalAsset))
                .monthlyIncome(nz(cashFlowEventRepository
                        .sumAmountByFlowTypeInPeriod(userId, FLOW_INCOME, start, end)))
                .monthlyExpense(nz(cashFlowEventRepository
                        .sumAmountByFlowTypeInPeriod(userId, FLOW_EXPENSE, start, end)))
                .etfHoldings(etfSnapshot.holdings())
                .etfSnapshotAmount(etfSnapshot.snapshotAmount())
                .stockHoldings(stockSnapshot.holdings())
                .stockSnapshotAmount(stockSnapshot.snapshotAmount())
                .menus(buildMenus(userId, snapshot))
                .build();
    }

    /**
     * 카테고리별 자산 집계(예수금만 계약). 계좌 잔액(예수금/현금)은 accountType으로,
     * 보유종목 평가액은 productType으로 분류해 더한다. 종목값이 deposit_balance에 없으므로 별도 합산 필수.
     */
    private Map<AssetCategory, BigDecimal> aggregateByCategory(AssetAggregator.AssetSnapshot snapshot) {
        Map<AssetCategory, BigDecimal> map = new EnumMap<>(AssetCategory.class);

        for (Account account : snapshot.accounts()) {
            BigDecimal balance = nz(account.getDepositBalance());
            if (balance.signum() == 0) {
                continue;
            }
            AssetCategory cat = AssetCategory.fromAccountType(account.getAccountType());
            if (cat == AssetCategory.CMA) cat = AssetCategory.STOCK;
            map.merge(cat, balance, BigDecimal::add);
        }

        List<HoldingWithProduct> holdings = snapshot.holdings();
        if (!holdings.isEmpty()) {
            Map<Long, ProductBatchItem> products = snapshot.products();
            for (HoldingWithProduct holding : holdings) {
                BigDecimal eval = nz(holding.getEvaluationAmount());
                if (eval.signum() == 0) {
                    continue;
                }
                ProductBatchItem product = products.get(holding.getProductId());
                String productType = product == null ? null : product.productType();
                map.merge(AssetCategory.fromProductType(productType), eval, BigDecimal::add);
            }
        }
        return map;
    }

    private AssetHubMenus buildMenus(Long userId, AssetAggregator.AssetSnapshot snapshot) {
        Optional<SalaryPlan> activePlan = salaryPlanRepository
                .findByUserUserIdAndStatus(userId, SalaryPlan.STATUS_ACTIVE);
        AssetHubMenus.SalaryMaking salaryMaking = activePlan
                .map(this::toSalaryMaking)
                .orElseGet(() -> toSalaryMaking(
                        cashFlowDiagnosisService.diagnose(userId),
                        salaryPlanRepository.existsByUserUserId(userId)
                ));

        return AssetHubMenus.builder()
                .salaryMaking(salaryMaking)
                .lifeStability(buildLifeStabilityPreview(userId, salaryMaking.achievementRate()))
                .investmentCheck(buildInvestmentCheckPreview(snapshot))
                // 후속 이슈에서 채움: 국민연금 연기(#5) / 월간 리포트(#6)
                .pensionDefer(AssetHubMenus.PensionDefer.builder().build())
                .retirementSim(new AssetHubMenus.RetirementSim(true))
                .monthlyReport(AssetHubMenus.MonthlyReport.builder().build())
                .build();
    }

    private AssetHubMenus.SalaryMaking toSalaryMaking(SalaryPlan plan) {
        return AssetHubMenus.SalaryMaking.builder()
                .achievementRate(toInt(plan.getLivingCostCoverageRate()))
                .targetAmount(plan.getTargetMonthlyLivingCost())
                .currentAmount(plan.getExpectedMonthlySalary())
                .hasActivePlan(true)
                .hasPlanHistory(true)
                .build();
    }

    private AssetHubMenus.SalaryMaking toSalaryMaking(CashFlowDiagnosisResponse cashFlow) {
        return toSalaryMaking(cashFlow, false);
    }

    private AssetHubMenus.SalaryMaking toSalaryMaking(CashFlowDiagnosisResponse cashFlow, boolean hasPlanHistory) {
        return AssetHubMenus.SalaryMaking.builder()
                .achievementRate(ratePercent(cashFlow.getMonthlyCashFlow(), cashFlow.getTargetMonthlyLivingCost()))
                .targetAmount(cashFlow.getTargetMonthlyLivingCost())
                .currentAmount(cashFlow.getMonthlyCashFlow())
                .hasActivePlan(false)
                .hasPlanHistory(hasPlanHistory)
                .build();
    }

    private AssetHubMenus.LifeStability buildLifeStabilityPreview(Long userId, Integer coverageRate) {
        try {
            LifeStabilityResponse latest = lifeStabilityService.getLatest(userId);
            return AssetHubMenus.LifeStability.builder()
                    .grade(latest.grade())
                    .gradeLabel(latest.gradeLabel())
                    .coverageRate(coverageRate)
                    .build();
        } catch (BaseException e) {
            if (e.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                // 아직 생활 안정도를 산출하지 않은 사용자 → 빈 미리보기
                return AssetHubMenus.LifeStability.builder().build();
            }
            throw e;
        }
    }

    /**
     * 투자 건강검진 미리보기 — 현금흐름 자산(비연금 비STOCK 보유) / 순자산 비율.
     * 상세({@link InvestmentCheckService})와 동일하게 {@link AssetBreakdown#cashflowAssetRatio()}를
     * 단일 출처로 쓰므로 허브 미리보기·상세 헤드라인·도넛 조각 숫자가 항상 일치한다.
     * 자산이 없으면(순자산 0) null 로 내려간다.
     */
    private AssetHubMenus.InvestmentCheck buildInvestmentCheckPreview(AssetAggregator.AssetSnapshot snapshot) {
        return new AssetHubMenus.InvestmentCheck(snapshot.breakdown().cashflowAssetRatio());
    }

    /** numerator/denominator 를 정수 % 로. 둘 중 하나라도 null 이거나 denominator 가 0 이면 null. */
    private Integer ratePercent(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private Integer toInt(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
