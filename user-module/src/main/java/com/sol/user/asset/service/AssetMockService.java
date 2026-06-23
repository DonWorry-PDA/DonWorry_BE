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
import com.sol.user.holding.entity.Holding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.insurance.entity.InsurancePolicy;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.entity.Pension;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetMockService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final PensionRepository pensionRepository;
    private final CashFlowEventRepository cashFlowEventRepository;
    private final AssetConnectionRepository assetConnectionRepository;
    private final DebtRepository debtRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final EtfPoolProvider etfPoolProvider;

    /**
     * 마이데이터 연동 목업은 사용자가 시나리오를 직접 고르지 않는다.
     * 로그인한 userId에 미리 배정된 생활 안정도 구간 시나리오를 사용한다.
     * (시연용 고정 매핑 — 런타임 재배정이 필요하면 개발용 seed API로 강제 전환한다.)
     */
    private static final Map<Long, MockType> SCENARIO_BY_USER = Map.of(
            1L, MockType.NEED_IMPROVEMENT,
            2L, MockType.NEED_COMPLEMENT,
            3L, MockType.STABLE
    );
    private static final MockType DEFAULT_SCENARIO = MockType.NEED_COMPLEMENT;

    /**
     * 사용자용 마이데이터 연결/재동기화. userId에 배정된 시나리오를 업서트한다.
     * 시나리오가 userId마다 고정이므로 재호출해도 같은 상태로 수렴하며 이전 데이터가 남지 않는다.
     */
    @Transactional
    public MockAssetResponse sync(Long userId) {
        return create(userId, SCENARIO_BY_USER.getOrDefault(userId, DEFAULT_SCENARIO));
    }

    /**
     * 개발/시연용 시드. 기존 목업 원천 데이터를 모두 지우고 지정한 시나리오로 새로 생성한다.
     * (StabilityScore 이력은 건드리지 않는다.)
     */
    @Transactional
    public MockAssetResponse seed(Long userId, MockType mockType) {
        if (mockType == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        deleteAllMockData(userId);
        return create(userId, mockType);
    }

    private void deleteAllMockData(Long userId) {
        List<Account> mockAccounts = accountRepository.findByUserUserId(userId).stream()
                .filter(account -> account.getAccountNumber() != null
                        && account.getAccountNumber().startsWith("MOCK-"))
                .toList();
        holdingRepository.deleteAll(holdingRepository.findByAccountIn(mockAccounts));
        accountRepository.deleteAll(mockAccounts);
        pensionRepository.deleteAll(pensionRepository.findByUserUserId(userId));
        debtRepository.deleteAll(debtRepository.findByUserUserId(userId));
        insurancePolicyRepository.deleteAll(insurancePolicyRepository.findByUserUserId(userId));
        assetConnectionRepository.deleteAll(assetConnectionRepository.findByUserUserId(userId));
        cashFlowEventRepository.deleteAll(cashFlowEventRepository.findByUserUserId(userId).stream()
                .filter(event -> "MYDATA_MOCK".equals(event.getSource()))
                .toList());
    }

    @Transactional
    public MockAssetResponse create(Long userId, MockType mockType) {
        if (mockType == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        Scenario scenario = scenarioOf(mockType);
        LocalDateTime generatedAt = LocalDateTime.now();

        List<Account> accounts = saveAssets(user, userId, scenario.assets());
        List<Holding> holdings = saveHoldings(accounts, scenario.holdings());
        List<Pension> pensions = savePensions(user, scenario);
        List<Debt> debts = saveDebts(user, scenario);
        List<InsurancePolicy> policies = saveInsurancePolicies(user, scenario);
        List<AssetConnection> connections = saveConnections(user, generatedAt);
        List<CashFlowEvent> events = saveCashflowEvents(user, scenario);

        return new MockAssetResponse(
                mockType,
                generatedAt,
                createAssetSummary(scenario),
                new MockGeneratedCounts(
                        connections.size(),
                        accounts.size(),
                        holdings.size(),
                        pensions.size(),
                        debts.size(),
                        policies.size(),
                        events.size()
                )
        );
    }

    private List<Account> saveAssets(User user, Long userId, List<AssetSeed> seeds) {
        List<Account> existing = accountRepository.findByUserUserId(userId).stream()
                .filter(account -> account.getAccountNumber() != null
                        && account.getAccountNumber().startsWith("MOCK-"))
                .toList();
        List<Account> desired = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            AssetSeed seed = seeds.get(i);
            desired.add(new Account(user, seed.category(), seed.institutionName(),
                    "MOCK-" + userId + "-" + (i + 1), seed.amount(), true));
        }
        return upsertByKey(existing, desired, Account::getAccountType,
                (target, seed) -> target.updateMock(seed.getInstitutionName(),
                        seed.getAccountNumber(), seed.getDepositBalance()),
                accountRepository::deleteAll, accountRepository::saveAll);
    }

    private List<Holding> saveHoldings(List<Account> accounts, List<HoldingSeed> seeds) {
        Account brokerage = accounts.stream()
                .filter(a -> "BROKERAGE".equals(a.getAccountType()))
                .findFirst().orElse(null);
        if (brokerage == null || seeds.isEmpty()) {
            return List.of();
        }
        Map<String, Long> tickerToProductId;
        try {
            tickerToProductId = etfPoolProvider.getPool().stream()
                    .filter(info -> info.productId() != null)
                    .collect(Collectors.toMap(EtfInfo::ticker, EtfInfo::productId, (a, b) -> a));
        } catch (Exception e) {
            return List.of();
        }

        boolean allMapped = seeds.stream().allMatch(s -> tickerToProductId.containsKey(s.ticker()));
        if (!allMapped) {
            throw new IllegalStateException("ETF 풀에 없는 티커가 HoldingSeed에 포함되어 있습니다.");
        }

        List<Holding> desired = seeds.stream()
                .map(seed -> new Holding(
                        brokerage,
                        tickerToProductId.get(seed.ticker()),
                        seed.evaluationAmount(),
                        seed.quantity()
                ))
                .toList();
        holdingRepository.deleteAll(holdingRepository.findByAccountIn(List.of(brokerage)));
        return holdingRepository.saveAll(desired);
    }

    private List<Pension> savePensions(User user, Scenario scenario) {
        List<Pension> desired = List.of(
                new Pension(user, "NATIONAL", scenario.monthlyPensionIncome(), false, 65),
                new Pension(user, "RETIREMENT", BigDecimal.ZERO, false, 65),
                new Pension(user, "PERSONAL", BigDecimal.ZERO, true, 65)
        );
        return upsertByKey(pensionRepository.findByUserUserId(user.getUserId()), desired,
                Pension::getPensionType,
                (target, seed) -> target.updateMock(seed.getExpectedMonthlyAmount(),
                        seed.getVariable(), seed.getStartAge()),
                pensionRepository::deleteAll, pensionRepository::saveAll);
    }

    private List<Debt> saveDebts(User user, Scenario scenario) {
        List<Debt> existing = debtRepository.findByUserUserId(user.getUserId());
        if (scenario.debtBalance().signum() == 0) {
            if (!existing.isEmpty()) {
                debtRepository.deleteAll(existing);
            }
            return List.of();
        }
        Debt debt;
        if (existing.isEmpty()) {
            debt = new Debt(user, "신한은행", "CREDIT_LOAN", scenario.debtBalance(),
                    scenario.monthlyLoanRepayment(), scenario.loanInterestRate(), LocalDate.now().plusYears(10));
        } else {
            debt = existing.get(0);
            debt.updateMock("신한은행", "CREDIT_LOAN", scenario.debtBalance(),
                    scenario.monthlyLoanRepayment(), scenario.loanInterestRate(), LocalDate.now().plusYears(10));
            if (existing.size() > 1) {
                debtRepository.deleteAll(existing.subList(1, existing.size()));
            }
        }
        return debtRepository.saveAll(List.of(debt));
    }

    private List<InsurancePolicy> saveInsurancePolicies(User user, Scenario scenario) {
        BigDecimal firstPremium = scenario.monthlyInsurancePremium().divide(BigDecimal.valueOf(3), 0, RoundingMode.DOWN);
        BigDecimal lastPremium = scenario.monthlyInsurancePremium().subtract(firstPremium.multiply(BigDecimal.valueOf(2)));
        BigDecimal firstReserve = scenario.medicalReserve().divide(BigDecimal.valueOf(3), 0, RoundingMode.DOWN);
        BigDecimal lastReserve = scenario.medicalReserve().subtract(firstReserve.multiply(BigDecimal.valueOf(2)));
        List<InsurancePolicy> desired = List.of(
                new InsurancePolicy(user, "신한라이프", "INDEMNITY", firstPremium, true, firstReserve),
                new InsurancePolicy(user, "신한라이프", "CANCER", firstPremium, true, firstReserve),
                new InsurancePolicy(user, "신한라이프", "NURSING", lastPremium, true, lastReserve)
        );
        return upsertByKey(insurancePolicyRepository.findByUserUserId(user.getUserId()), desired,
                InsurancePolicy::getInsuranceType,
                (target, seed) -> target.updateMock(seed.getInstitutionName(), seed.getMonthlyPremium(),
                        seed.getActive(), seed.getMedicalReserve()),
                insurancePolicyRepository::deleteAll, insurancePolicyRepository::saveAll);
    }

    private List<AssetConnection> saveConnections(User user, LocalDateTime syncedAt) {
        List<AssetConnection> desired = List.of(
                new AssetConnection(user, "신한은행", "BANK", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한투자증권", "SECURITIES", "CONNECTED", syncedAt),
                new AssetConnection(user, "국민연금공단", "PENSION", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한라이프", "INSURANCE", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한카드", "CARD", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한은행", "LOAN", "CONNECTED", syncedAt)
        );
        return upsertByKey(assetConnectionRepository.findByUserUserId(user.getUserId()), desired,
                AssetConnection::getCategory,
                (target, seed) -> target.updateMock(seed.getInstitutionName(), seed.getLastSyncedAt()),
                assetConnectionRepository::deleteAll, assetConnectionRepository::saveAll);
    }

    private List<CashFlowEvent> saveCashflowEvents(User user, Scenario scenario) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        BigDecimal interestIncome = scenario.monthlyFinancialIncome()
                .multiply(BigDecimal.valueOf(30))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal dividendIncome = scenario.monthlyFinancialIncome().subtract(interestIncome);

        List<CashFlowEvent> desired = List.of(
                event(user, month.withDayOfMonth(5), "PENSION", "국민연금 입금", scenario.monthlyPensionIncome(), "INCOME"),
                event(user, month.withDayOfMonth(10), "MAINTENANCE", "관리비", scenario.monthlyMaintenanceExpense(), "EXPENSE"),
                event(user, month.withDayOfMonth(15), "INSURANCE", "보험료", scenario.monthlyInsurancePremium(), "EXPENSE"),
                event(user, month.withDayOfMonth(20), "INTEREST", "예금 이자", interestIncome, "INCOME"),
                event(user, month.withDayOfMonth(25), "CARD", "카드대금", scenario.monthlyCardExpense(), "EXPENSE"),
                event(user, month.withDayOfMonth(27), "LOAN", "대출 상환", scenario.monthlyLoanRepayment(), "EXPENSE"),
                event(user, month.withDayOfMonth(28), "DIVIDEND", "ETF 배당금", dividendIncome, "INCOME")
        );
        List<CashFlowEvent> existing = cashFlowEventRepository.findByUserUserId(user.getUserId()).stream()
                .filter(cashFlowEvent -> "MYDATA_MOCK".equals(cashFlowEvent.getSource()))
                .toList();
        return upsertByKey(existing, desired, CashFlowEvent::getEventType,
                (target, seed) -> target.updateMock(seed.getEventDate(), seed.getTitle(),
                        seed.getAmount(), seed.getFlowType()),
                cashFlowEventRepository::deleteAll, cashFlowEventRepository::saveAll);
    }

    /**
     * 유형 키 기준 업서트. 같은 키의 기존 행은 updateFn으로 수정해 재사용하고,
     * desired에 없는 기존 행은 삭제, desired에만 있는 키는 새로 추가한다.
     */
    private <E> List<E> upsertByKey(
            List<E> existing,
            List<E> desired,
            Function<E, String> keyFn,
            BiConsumer<E, E> updateFn,
            Consumer<List<E>> deleteAll,
            Function<List<E>, List<E>> saveAll) {
        Map<String, E> existingByKey = new LinkedHashMap<>();
        List<E> obsolete = new ArrayList<>();
        for (E entity : existing) {
            E duplicate = existingByKey.putIfAbsent(keyFn.apply(entity), entity);
            if (duplicate != null) {
                obsolete.add(entity);
            }
        }
        List<E> result = new ArrayList<>();
        for (E seed : desired) {
            E found = existingByKey.remove(keyFn.apply(seed));
            if (found == null) {
                result.add(seed);
            } else {
                updateFn.accept(found, seed);
                result.add(found);
            }
        }
        obsolete.addAll(existingByKey.values());
        deleteAll.accept(obsolete);
        return saveAll.apply(result);
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
        return new AssetSummaryResponse(
                totalAsset,
                scenario.debtBalance(),
                totalAsset.subtract(scenario.debtBalance()),
                groups
        );
    }

    private Scenario scenarioOf(MockType mockType) {
        return switch (mockType) {
            case NEED_IMPROVEMENT -> new Scenario(
                    List.of(
                            asset("CMA", "신한은행", 6_000_000),
                            asset("DEPOSIT", "신한은행", 12_000_000),
                            asset("BROKERAGE", "신한투자증권", 80_000_000),
                            asset("IRP", "신한투자증권", 25_000_000)
                    ),
                    List.of(
                            holding("433330", 40_000_000, 2_000),  // SOL 미국S&P500
                            holding("476030", 40_000_000, 2_500)   // SOL 미국나스닥100
                    ),
                    money(75_000_000), money(650_000), new BigDecimal("4.80"),
                    money(5_400_000), money(600_000), money(104_000),
                    money(250_000), money(180_000), money(1_250_000)
            );
            case NEED_COMPLEMENT -> new Scenario(
                    List.of(
                            asset("CMA", "신한은행", 18_000_000),
                            asset("DEPOSIT", "신한은행", 45_000_000),
                            asset("BROKERAGE", "신한투자증권", 55_000_000),
                            asset("IRP", "신한투자증권", 65_000_000),
                            asset("PENSION_SAVING", "신한투자증권", 25_000_000)
                    ),
                    List.of(
                            holding("433330", 30_000_000, 1_500),  // SOL 미국S&P500
                            holding("292500", 25_000_000, 2_500)   // SOL KRX300
                    ),
                    money(30_000_000), money(300_000), new BigDecimal("4.10"),
                    money(4_200_000), money(1_150_000), money(148_000),
                    money(200_000), money(180_000), money(1_400_000)
            );
            case STABLE -> new Scenario(
                    List.of(
                            asset("CMA", "신한은행", 35_000_000),
                            asset("DEPOSIT", "신한은행", 90_000_000),
                            asset("BROKERAGE", "신한투자증권", 130_000_000),
                            asset("IRP", "신한투자증권", 120_000_000),
                            asset("PENSION_SAVING", "신한투자증권", 60_000_000)
                    ),
                    List.of(
                            holding("446720", 55_000_000, 5_000),  // SOL 미국배당다우존스
                            holding("438560", 45_000_000, 400),    // SOL 국고채3년
                            holding("433330", 30_000_000, 1_500)   // SOL 미국S&P500
                    ),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    money(6_000_000), money(2_000_000), money(464_000),
                    money(180_000), money(180_000), money(1_750_000)
            );
        };
    }

    private static AssetSeed asset(String category, String institutionName, long amount) {
        return new AssetSeed(category, institutionName, money(amount));
    }

    private static HoldingSeed holding(String ticker, long amount, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Holding 수량은 0보다 커야 합니다.");
        }
        return new HoldingSeed(ticker, money(amount), BigDecimal.valueOf(quantity));
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount);
    }

    private record AssetSeed(String category, String institutionName, BigDecimal amount) {
    }

    private record HoldingSeed(String ticker, BigDecimal evaluationAmount, BigDecimal quantity) {
    }

    private record Scenario(
            List<AssetSeed> assets,
            List<HoldingSeed> holdings,
            BigDecimal debtBalance,
            BigDecimal monthlyLoanRepayment,
            BigDecimal loanInterestRate,
            BigDecimal medicalReserve,
            BigDecimal monthlyPensionIncome,
            BigDecimal monthlyFinancialIncome,
            BigDecimal monthlyInsurancePremium,
            BigDecimal monthlyMaintenanceExpense,
            BigDecimal monthlyCardExpense
    ) {
    }
}
