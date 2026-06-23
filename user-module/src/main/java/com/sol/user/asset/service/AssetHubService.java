package com.sol.user.asset.service;

import com.sol.common.exception.BaseException;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetAllocationItem;
import com.sol.user.asset.dto.AssetHubMenus;
import com.sol.user.asset.dto.AssetHubResponse;
import com.sol.user.asset.type.AssetCategory;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.service.CashFlowDiagnosisService;
import com.sol.user.stability.dto.LifeStabilityResponse;
import com.sol.user.stability.service.LifeStabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetHubService {

    private static final String FLOW_INCOME = "INCOME";
    private static final String FLOW_EXPENSE = "EXPENSE";

    private final AccountRepository accountRepository;
    private final CashFlowEventRepository cashFlowEventRepository;
    private final CashFlowDiagnosisService cashFlowDiagnosisService;
    private final LifeStabilityService lifeStabilityService;

    public AssetHubResponse getHub(Long userId) {
        Map<AssetCategory, BigDecimal> byCategory = aggregateByCategory(
                accountRepository.findByUserUserId(userId));
        BigDecimal totalAsset = byCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth thisMonth = YearMonth.now();
        LocalDate start = thisMonth.atDay(1);
        LocalDate end = thisMonth.atEndOfMonth();

        return AssetHubResponse.builder()
                .totalAsset(totalAsset)
                // 월간 자산 스냅샷(#6) 도입 전까지 증감 계산 불가 → null / FLAT
                .changeAmount(null)
                .changeDirection("FLAT")
                .allocation(toAllocation(byCategory, totalAsset))
                .monthlyIncome(nz(cashFlowEventRepository
                        .sumAmountByFlowTypeInPeriod(userId, FLOW_INCOME, start, end)))
                .monthlyExpense(nz(cashFlowEventRepository
                        .sumAmountByFlowTypeInPeriod(userId, FLOW_EXPENSE, start, end)))
                .menus(buildMenus(userId))
                .build();
    }

    private Map<AssetCategory, BigDecimal> aggregateByCategory(List<Account> accounts) {
        Map<AssetCategory, BigDecimal> map = new EnumMap<>(AssetCategory.class);
        for (Account account : accounts) {
            BigDecimal balance = nz(account.getDepositBalance());
            if (balance.signum() == 0) {
                continue;
            }
            map.merge(AssetCategory.fromAccountType(account.getAccountType()), balance, BigDecimal::add);
        }
        return map;
    }

    /**
     * 카테고리별 금액을 정수 % 로 변환한다.
     * 내림 후 남는 잔여 %를 소수부가 큰 항목부터 1씩 배분해 합이 정확히 100 이 되도록 보정한다.
     * 표시 순서는 enum 선언 순서(연금·예금·ETF·주식·기타) 고정.
     */
    private List<AssetAllocationItem> toAllocation(Map<AssetCategory, BigDecimal> byCategory, BigDecimal total) {
        if (total.signum() <= 0) {
            return List.of();
        }

        List<Bucket> buckets = new ArrayList<>();
        for (Map.Entry<AssetCategory, BigDecimal> entry : byCategory.entrySet()) {
            BigDecimal pct = entry.getValue()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 4, RoundingMode.HALF_UP);
            int floor = pct.setScale(0, RoundingMode.DOWN).intValue();
            BigDecimal remainder = pct.subtract(BigDecimal.valueOf(floor));
            buckets.add(new Bucket(entry.getKey(), floor, remainder));
        }

        int assigned = buckets.stream().mapToInt(Bucket::ratio).sum();
        int leftover = 100 - assigned;
        buckets.stream()
                .sorted(Comparator.comparing(Bucket::remainder).reversed())
                .limit(Math.max(leftover, 0))
                .forEach(Bucket::increment);

        return buckets.stream()
                .sorted(Comparator.comparing(b -> b.category.ordinal()))
                .map(b -> new AssetAllocationItem(b.category.getLabel(), b.ratio()))
                .toList();
    }

    private AssetHubMenus buildMenus(Long userId) {
        CashFlowDiagnosisResponse cashFlow = cashFlowDiagnosisService.diagnose(userId);
        Integer coverageRate = ratePercent(cashFlow.getMonthlyCashFlow(), cashFlow.getTargetMonthlyLivingCost());

        return AssetHubMenus.builder()
                .salaryMaking(AssetHubMenus.SalaryMaking.builder()
                        .achievementRate(coverageRate)
                        .targetAmount(cashFlow.getTargetMonthlyLivingCost())
                        .currentAmount(cashFlow.getMonthlyCashFlow())
                        .build())
                .lifeStability(buildLifeStabilityPreview(userId))
                // 후속 이슈에서 채움: 투자 건강검진(#2) / 국민연금 연기(#5) / 월간 리포트(#6)
                .investmentCheck(new AssetHubMenus.InvestmentCheck(null))
                .pensionDefer(AssetHubMenus.PensionDefer.builder().build())
                .retirementSim(new AssetHubMenus.RetirementSim(true))
                .monthlyReport(AssetHubMenus.MonthlyReport.builder().build())
                .build();
    }

    private AssetHubMenus.LifeStability buildLifeStabilityPreview(Long userId) {
        try {
            LifeStabilityResponse latest = lifeStabilityService.getLatest(userId);
            Integer coverageRate = latest.metrics() == null
                    ? null
                    : toInt(latest.metrics().cashflowCoverageRate());
            return AssetHubMenus.LifeStability.builder()
                    .grade(latest.grade())
                    .gradeLabel(latest.gradeLabel())
                    .coverageRate(coverageRate)
                    .build();
        } catch (BaseException e) {
            // 아직 생활 안정도를 산출하지 않은 사용자 → 빈 미리보기
            return AssetHubMenus.LifeStability.builder().build();
        }
    }

    /** numerator/denominator 를 정수 % 로. denominator 가 0/null 이면 null. */
    private Integer ratePercent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
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

    /** 분포 계산용 가변 버킷. */
    private static final class Bucket {
        private final AssetCategory category;
        private int ratio;
        private final BigDecimal remainder;

        private Bucket(AssetCategory category, int ratio, BigDecimal remainder) {
            this.category = category;
            this.ratio = ratio;
            this.remainder = remainder;
        }

        private int ratio() {
            return ratio;
        }

        private BigDecimal remainder() {
            return remainder;
        }

        private void increment() {
            ratio++;
        }
    }
}
