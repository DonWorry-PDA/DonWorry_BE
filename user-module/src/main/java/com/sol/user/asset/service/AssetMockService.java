package com.sol.user.asset.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetGroupSummary;
import com.sol.user.asset.dto.AssetSummaryResponse;
import com.sol.user.asset.dto.MockAssetResponse;
import com.sol.user.asset.dto.MockGeneratedCounts;
import com.sol.user.asset.type.MockType;
import com.sol.user.assetconnection.entity.AssetConnection;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.entity.Debt;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.insurance.entity.InsurancePolicy;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.entity.Pension;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.stability.dto.LifeStabilityResponse;
import com.sol.user.stability.repository.StabilityScoreRepository;
import com.sol.user.stability.service.LifeStabilityService;
import com.sol.user.trade.repository.TradeHistoryRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssetMockService {

    private static final BigDecimal TARGET_LIVING_EXPENSE = money(2_200_000);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final PensionRepository pensionRepository;
    private final UserGoalRepository userGoalRepository;
    private final CashFlowEventRepository cashFlowEventRepository;
    private final AssetConnectionRepository assetConnectionRepository;
    private final DebtRepository debtRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final StabilityScoreRepository stabilityScoreRepository;
    private final LifeStabilityService lifeStabilityService;

    @Transactional
    public MockAssetResponse create(Long userId, MockType mockType) {
        if (mockType == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        Scenario scenario = scenarioOf(mockType);
        LocalDateTime generatedAt = LocalDateTime.now();

        replaceExistingData(userId);
        user.applyMyDataMockProfile(true, true);
        userGoalRepository.save(new UserGoal(
                user,
                TARGET_LIVING_EXPENSE,
                scenario.monthlyMedicalExpense(),
                generatedAt
        ));

        List<Account> accounts = saveAssets(user, userId, scenario.assets());
        List<Pension> pensions = savePensions(user, scenario);
        List<Debt> debts = saveDebts(user, scenario);
        List<InsurancePolicy> policies = saveInsurancePolicies(user, scenario);
        List<AssetConnection> connections = saveConnections(user, generatedAt);
        List<CashFlowEvent> events = saveCashflowEvents(user, scenario);

        LifeStabilityResponse stability = lifeStabilityService.recalculateFromUserData(userId);

        return new MockAssetResponse(
                mockType,
                generatedAt,
                createAssetSummary(scenario),
                stability,
                new MockGeneratedCounts(
                        connections.size(),
                        accounts.size(),
                        pensions.size(),
                        debts.size(),
                        policies.size(),
                        events.size()
                )
        );
    }

    private void replaceExistingData(Long userId) {
        tradeHistoryRepository.deleteByAccountUserUserId(userId);
        holdingRepository.deleteByAccountUserUserId(userId);
        accountRepository.deleteByUserUserId(userId);
        pensionRepository.deleteByUserUserId(userId);
        cashFlowEventRepository.deleteByUserUserId(userId);
        userGoalRepository.deleteByUserUserId(userId);
        assetConnectionRepository.deleteByUserUserId(userId);
        debtRepository.deleteByUserUserId(userId);
        insurancePolicyRepository.deleteByUserUserId(userId);
        stabilityScoreRepository.deleteByUserId(userId);
    }

    private List<Account> saveAssets(User user, Long userId, List<AssetSeed> seeds) {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            AssetSeed seed = seeds.get(i);
            accounts.add(new Account(
                    user,
                    seed.category(),
                    seed.institutionName(),
                    "MOCK-" + userId + "-" + (i + 1),
                    seed.amount(),
                    true
            ));
        }
        return accountRepository.saveAll(accounts);
    }

    private List<Pension> savePensions(User user, Scenario scenario) {
        return pensionRepository.saveAll(List.of(
                new Pension(user, "NATIONAL", scenario.monthlyPensionIncome(), false, 65),
                new Pension(user, "RETIREMENT", BigDecimal.ZERO, false, 65),
                new Pension(user, "PERSONAL", BigDecimal.ZERO, true, 65)
        ));
    }

    private List<Debt> saveDebts(User user, Scenario scenario) {
        if (scenario.debtBalance().signum() == 0) {
            return List.of();
        }
        return debtRepository.saveAll(List.of(new Debt(
                user,
                "신한은행",
                "CREDIT_LOAN",
                scenario.debtBalance(),
                scenario.monthlyLoanRepayment(),
                scenario.loanInterestRate(),
                LocalDate.now().plusYears(10)
        )));
    }

    private List<InsurancePolicy> saveInsurancePolicies(User user, Scenario scenario) {
        BigDecimal firstPremium = scenario.monthlyInsurancePremium().divide(BigDecimal.valueOf(3), 0, RoundingMode.DOWN);
        BigDecimal lastPremium = scenario.monthlyInsurancePremium().subtract(firstPremium.multiply(BigDecimal.valueOf(2)));
        BigDecimal firstReserve = scenario.medicalReserve().divide(BigDecimal.valueOf(3), 0, RoundingMode.DOWN);
        BigDecimal lastReserve = scenario.medicalReserve().subtract(firstReserve.multiply(BigDecimal.valueOf(2)));
        return insurancePolicyRepository.saveAll(List.of(
                new InsurancePolicy(user, "신한라이프", "INDEMNITY", firstPremium, true, firstReserve),
                new InsurancePolicy(user, "신한라이프", "CANCER", firstPremium, true, firstReserve),
                new InsurancePolicy(user, "신한라이프", "NURSING", lastPremium, true, lastReserve)
        ));
    }

    private List<AssetConnection> saveConnections(User user, LocalDateTime syncedAt) {
        return assetConnectionRepository.saveAll(List.of(
                new AssetConnection(user, "신한은행", "BANK", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한투자증권", "SECURITIES", "CONNECTED", syncedAt),
                new AssetConnection(user, "국민연금공단", "PENSION", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한라이프", "INSURANCE", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한카드", "CARD", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한은행", "LOAN", "CONNECTED", syncedAt)
        ));
    }

    private List<CashFlowEvent> saveCashflowEvents(User user, Scenario scenario) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        BigDecimal interestIncome = scenario.monthlyFinancialIncome()
                .multiply(BigDecimal.valueOf(30))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal dividendIncome = scenario.monthlyFinancialIncome().subtract(interestIncome);

        return cashFlowEventRepository.saveAll(List.of(
                event(user, month.withDayOfMonth(5), "PENSION", "국민연금 입금", scenario.monthlyPensionIncome(), "INCOME"),
                event(user, month.withDayOfMonth(10), "MAINTENANCE", "관리비", scenario.monthlyMaintenanceExpense(), "EXPENSE"),
                event(user, month.withDayOfMonth(15), "INSURANCE", "보험료", scenario.monthlyInsurancePremium(), "EXPENSE"),
                event(user, month.withDayOfMonth(20), "INTEREST", "예금 이자", interestIncome, "INCOME"),
                event(user, month.withDayOfMonth(25), "CARD", "카드대금", scenario.monthlyCardExpense(), "EXPENSE"),
                event(user, month.withDayOfMonth(27), "LOAN", "대출 상환", scenario.monthlyLoanRepayment(), "EXPENSE"),
                event(user, month.withDayOfMonth(28), "DIVIDEND", "ETF 배당금", dividendIncome, "INCOME")
        ));
    }

    private CashFlowEvent event(User user, LocalDate date, String type, String title,
                                BigDecimal amount, String flowType) {
        return new CashFlowEvent(
                user,
                date,
                type,
                title,
                amount,
                flowType,
                "SCHEDULED",
                true,
                "MYDATA_MOCK"
        );
    }

    private AssetSummaryResponse createAssetSummary(Scenario scenario) {
        Map<String, BigDecimal> grouped = new LinkedHashMap<>();
        scenario.assets().forEach(seed -> grouped.merge(seed.category(), seed.amount(), BigDecimal::add));
        List<AssetGroupSummary> groups = grouped.entrySet().stream()
                .map(entry -> new AssetGroupSummary(entry.getKey(), entry.getValue()))
                .toList();

        BigDecimal totalAsset = scenario.assets().stream()
                .map(AssetSeed::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal securedCashflow = scenario.monthlyPensionIncome().add(scenario.monthlyFinancialIncome());
        BigDecimal monthlyGap = securedCashflow.subtract(TARGET_LIVING_EXPENSE);
        BigDecimal coverageRate = securedCashflow
                .multiply(BigDecimal.valueOf(100))
                .divide(TARGET_LIVING_EXPENSE, 0, RoundingMode.HALF_UP);

        return new AssetSummaryResponse(
                totalAsset,
                scenario.debtBalance(),
                totalAsset.subtract(scenario.debtBalance()),
                TARGET_LIVING_EXPENSE,
                securedCashflow,
                monthlyGap,
                coverageRate,
                groups
        );
    }

    private Scenario scenarioOf(MockType mockType) {
        return switch (mockType) {
            case NEED_IMPROVEMENT -> new Scenario(
                    List.of(
                            asset("CHECKING_CMA", "신한은행", 6_000_000),
                            asset("DEPOSIT_SAVING", "신한은행", 12_000_000),
                            asset("STOCK_ETF_FUND", "신한투자증권", 80_000_000),
                            asset("RETIREMENT_PENSION", "신한투자증권", 25_000_000)
                    ),
                    money(75_000_000), money(650_000), new BigDecimal("4.80"),
                    money(450_000), money(5_400_000), money(600_000), money(104_000),
                    money(250_000), money(180_000), money(1_250_000)
            );
            case NEED_COMPLEMENT -> new Scenario(
                    List.of(
                            asset("CHECKING_CMA", "신한은행", 18_000_000),
                            asset("DEPOSIT_SAVING", "신한은행", 45_000_000),
                            asset("STOCK_ETF_FUND", "신한투자증권", 55_000_000),
                            asset("RETIREMENT_PENSION", "신한투자증권", 65_000_000),
                            asset("PERSONAL_PENSION", "신한투자증권", 25_000_000)
                    ),
                    money(30_000_000), money(300_000), new BigDecimal("4.10"),
                    money(350_000), money(4_200_000), money(1_150_000), money(148_000),
                    money(200_000), money(180_000), money(1_400_000)
            );
            case STABLE -> new Scenario(
                    List.of(
                            asset("CHECKING_CMA", "신한은행", 35_000_000),
                            asset("DEPOSIT_SAVING", "신한은행", 90_000_000),
                            asset("BOND", "신한투자증권", 45_000_000),
                            asset("MONTHLY_DIVIDEND_ETF", "신한투자증권", 55_000_000),
                            asset("FUND", "신한투자증권", 30_000_000),
                            asset("RETIREMENT_PENSION", "신한투자증권", 120_000_000),
                            asset("PERSONAL_PENSION", "신한투자증권", 60_000_000)
                    ),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    money(250_000), money(6_000_000), money(2_000_000), money(464_000),
                    money(180_000), money(180_000), money(1_750_000)
            );
        };
    }

    private static AssetSeed asset(String category, String institutionName, long amount) {
        return new AssetSeed(category, institutionName, money(amount));
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount);
    }

    private record AssetSeed(String category, String institutionName, BigDecimal amount) {
    }

    private record Scenario(
            List<AssetSeed> assets,
            BigDecimal debtBalance,
            BigDecimal monthlyLoanRepayment,
            BigDecimal loanInterestRate,
            BigDecimal monthlyMedicalExpense,
            BigDecimal medicalReserve,
            BigDecimal monthlyPensionIncome,
            BigDecimal monthlyFinancialIncome,
            BigDecimal monthlyInsurancePremium,
            BigDecimal monthlyMaintenanceExpense,
            BigDecimal monthlyCardExpense
    ) {
    }
}
