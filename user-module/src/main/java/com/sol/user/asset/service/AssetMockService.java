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
import com.sol.user.assetconnection.domain.ConnectedInstitutions;
import com.sol.user.assetconnection.entity.AssetConnection;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.cashflow.MonthlyVariation;
import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.entity.Debt;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.holding.dto.StockTickerProductId;
import com.sol.user.holding.entity.Holding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.holding.service.EtfDividendCalculator;
import com.sol.user.insurance.entity.InsurancePolicy;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.entity.Pension;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.report.entity.MonthlyReport;
import com.sol.user.report.repository.MonthlyReportRepository;
import com.sol.user.stability.service.LifeStabilityService;
import com.sol.user.survey.entity.SurveyResponse;
import com.sol.user.survey.repository.SurveyResponseRepository;
import com.sol.user.trade.entity.TradeHistory;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
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
    private final LifeStabilityService lifeStabilityService;
    private final DepositDetailClient depositDetailClient;
    private final UserGoalRepository userGoalRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final MonthlyReportRepository monthlyReportRepository;
    private final EtfDividendCalculator etfDividendCalculator;
    private final ProductBatchClient productBatchClient;

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

    // 월급만들기 설문 문항 코드. 시드/정리는 이 세 문항으로만 한정해 다른 설문 응답을 건드리지 않는다.
    private static final List<String> SALARY_SURVEY_CODES =
            List.of("MONTHLY_SALARY_Q1", "MONTHLY_SALARY_Q2", "MONTHLY_SALARY_Q3");

    // Days 1-28 excluding fixed-event days (5=pension, 10=maintenance, 15=insurance, 20=interest, 27=loan, 28=dividend)
    private static final int[] TEMPLATE_DAYS = {
            1, 2, 3, 4, 6, 7, 8, 9, 11, 12, 13, 14,
            16, 17, 18, 19, 21, 22, 23, 24, 25, 26
    };

    // Stock trade days — weekday-representative days spread evenly, avoiding fixed-event days
    private static final int[] STOCK_TRADE_DAYS = {
            2, 3, 4, 7, 8, 9, 11, 13, 16, 18, 21, 23, 25, 26
    };

    /**
     * 사용자용 마이데이터 연결/재동기화. userId에 배정된 시나리오를 업서트한다.
     * 시나리오가 userId마다 고정이므로 재호출해도 같은 상태로 수렴하며 이전 데이터가 남지 않는다.
     */
    @Transactional
    public MockAssetResponse sync(Long userId) {
        // 실사용자 경로: 온보딩·설문은 비어있을 때만 채워, 사용자가 직접 입력한 값을 덮지 않는다.
        // 성향은 시나리오 기본값(null override) — 실사용자 성향은 증권 KYC 연동 시 대체된다.
        return create(userId, SCENARIO_BY_USER.getOrDefault(userId, DEFAULT_SCENARIO), false, null);
    }

    /**
     * 개발/시연용 시드. 기존 목업 원천 데이터를 모두 지우고 지정한 시나리오로 새로 생성한다.
     * 원천 데이터가 갱신되면 {@link #create}에서 생활 안정도도 함께 재계산된다.
     */
    @Transactional
    public MockAssetResponse seed(Long userId, MockType mockType) {
        return seed(userId, mockType, null);
    }

    /**
     * 개발/시연용 시드. 기존 목업 원천 데이터를 모두 지우고 지정한 시나리오로 새로 생성한다.
     * 투자성향(KYC)은 자산 시나리오와 독립된 축이라 따로 지정할 수 있다 — null이면 시나리오 기본 성향을 쓴다.
     * 같은 자산에 성향만 5단계로 바꿔, 운용등급(성향 상한)·권유가능등급 필터가 어떻게 달라지는지 시연하는 용도.
     */
    @Transactional
    public MockAssetResponse seed(Long userId, MockType mockType, InvestmentPropensity propensity) {
        if (mockType == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        deleteAllMockData(userId);
        // 개발/시연 경로: 온보딩·설문까지 시나리오 값으로 강제 세팅해 완전 결정적 페르소나를 만든다.
        return create(userId, mockType, true, propensity);
    }

    private void deleteAllMockData(Long userId) {
        List<Account> mockAccounts = accountRepository.findByUserUserId(userId).stream()
                .filter(account -> account.getAccountNumber() != null
                        && account.getAccountNumber().startsWith("MOCK-"))
                .toList();
        List<Long> mockAccountIds = mockAccounts.stream().map(Account::getAccountId).toList();
        if (!mockAccountIds.isEmpty()) {
            // 거래내역·보유종목은 mock 계좌에만 시드되므로 계좌 삭제 전 mock 범위로만 정리한다.
            // userId 전체로 지우면 실계좌에 남은 비목업 거래내역까지 사라지므로 mockAccountIds로 한정한다(FK 해소).
            tradeHistoryRepository.deleteByAccountAccountIdIn(mockAccountIds);
            holdingRepository.deleteAllByAccountIdIn(mockAccountIds);
        }
        accountRepository.deleteAll(mockAccounts);
        pensionRepository.deleteAll(pensionRepository.findByUserUserId(userId));
        debtRepository.deleteAll(debtRepository.findByUserUserId(userId));
        insurancePolicyRepository.deleteAll(insurancePolicyRepository.findByUserUserId(userId));
        assetConnectionRepository.deleteAll(assetConnectionRepository.findByUserUserId(userId));
        cashFlowEventRepository.deleteAll(cashFlowEventRepository.findByUserUserId(userId).stream()
                .filter(event -> "MYDATA_MOCK".equals(event.getSource()))
                .toList());
    }

    /** 완전 결정적 빌드(온보딩·설문 강제 세팅 포함). 개발/테스트 진입점. 성향은 시나리오 기본값. */
    @Transactional
    public MockAssetResponse create(Long userId, MockType mockType) {
        return create(userId, mockType, true, null);
    }

    /**
     * @param propensityOverride 투자성향(KYC) 강제 지정. null이면 자산 시나리오의 기본 성향을 쓴다.
     *                           자산 시나리오와 독립된 축이라, 같은 자산에 성향만 바꿔 운용등급 차등을 시연할 수 있다.
     */
    @Transactional
    public MockAssetResponse create(Long userId, MockType mockType, boolean force,
                                    InvestmentPropensity propensityOverride) {
        if (mockType == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        Scenario scenario = scenarioOf(mockType);
        LocalDateTime generatedAt = LocalDateTime.now();

        // 투자성향(KYC) — 자산 시나리오와 독립. #118 권유가능등급 필터·운용등급 상한(gradeLimit)의 입력.
        // override가 있으면 항상 적용(명시적 시드 의도)하고, 없으면 시나리오 기본 성향을 쓰되
        // 실사용자 재동기화(force=false)에서는 이미 저장된 KYC 성향을 덮지 않는다(온보딩·설문과 동일 가드).
        if (propensityOverride != null) {
            user.assignInvestmentPropensity(propensityOverride);
        } else if (force || user.getInvestmentPropensity() == null) {
            user.assignInvestmentPropensity(scenario.propensity());
        }
        // 온보딩(나이·은퇴·국민연금수령·목표생활비)과 월급설문은 cashflow보다 먼저 시드한다.
        // buildMonthEvents가 user.getNationalPensionReceiving()으로 연금 입금 이벤트 여부를 판단하기 때문.
        saveOnboarding(user, scenario, force);
        saveSurvey(user, scenario, force);

        List<Account> accounts = saveAssets(user, userId, scenario);
        linkDepositProductId(accounts);
        List<Holding> holdings = saveHoldings(accounts, scenario.holdings(), scenario.stocks());
        List<Pension> pensions = savePensions(user, scenario);
        List<Debt> debts = saveDebts(user, scenario);
        List<InsurancePolicy> policies = saveInsurancePolicies(user, scenario);
        SavedConnections connections = saveConnections(user, generatedAt);
        List<CashFlowEvent> events = saveCashflowEvents(user, scenario, accounts);
        saveTradeHistory(accounts, holdings);

        // 보유 평가액(현금 제외)은 ① 응답 총자산(BROKERAGE 합산)과 ② 월별 시세변동 스냅샷 베이스에 함께 쓰인다.
        // 개별주는 '수량 × 현재가'라 현재가 조회(RestClient)가 필요한데, 두 용도가 같은 결과를 재사용하도록
        // 한 번만 계산한다(중복 HTTP 호출 제거 + 응답·스냅샷 평가 기준 동일 보장).
        BigDecimal holdingsValuation = holdingsMarketValue(holdings);
        // 자산 요약은 한 번만 계산해 월별 스냅샷 백필과 응답에 함께 쓴다.
        AssetSummaryResponse assetSummary = createAssetSummary(accounts, scenario.debtBalance(), holdingsValuation);
        // 월별 총자산 스냅샷(과거 12개월)을 "실제 월별 순현금흐름 + 배당 + 시세변동"에서 역산해 백필.
        saveMonthlyReportSnapshots(user, userId, assetSummary.totalAsset(), holdingsValuation, events);

        // 원천 데이터 저장 직후 생활 안정도를 재계산해 항상 최신 결과가 존재하도록 한다.
        // 온보딩(UserGoal) 전 사용자는 내부에서 스킵된다.
        lifeStabilityService.recalculateFromUserDataIfReady(userId);

        return new MockAssetResponse(
                mockType,
                generatedAt,
                assetSummary,
                new MockGeneratedCounts(
                        connections.generatedRows().size(),
                        accounts.size(),
                        holdings.size(),
                        pensions.size(),
                        debts.size(),
                        policies.size(),
                        events.size()
                ),
                (int) ConnectedInstitutions.count(connections.connectedRows())
        );
    }

    private void linkDepositProductId(List<Account> accounts) {
        List<Account> depositAccounts = accounts.stream()
                .filter(a -> "DEPOSIT".equals(a.getAccountType()))
                .toList();

        // productId가 없는 첫 번째 DEPOSIT 계정에 상품 연결 후 저장
        depositAccounts.stream()
                .filter(a -> a.getProductId() == null)
                .findFirst()
                .ifPresent(depositAccount -> depositDetailClient.fetchFirstDepositProductId()
                        .ifPresent(productId -> {
                            depositAccount.linkDepositProduct(productId);
                            accountRepository.save(depositAccount);
                        }));

        // productId는 있지만 openedAt이 없는 모든 DEPOSIT 계정에 가입일 초기화
        // (기존 데이터 + 방금 productId가 연결된 계정 모두 커버)
        depositAccounts.stream()
                .filter(a -> a.getProductId() != null && a.getOpenedAt() == null)
                .forEach(a -> {
                    a.initOpenedAt(LocalDate.now().minusMonths(10));
                    accountRepository.save(a);
                });
    }

    private List<Account> saveAssets(User user, Long userId, Scenario scenario) {
        List<Account> existing = accountRepository.findByUserUserId(userId).stream()
                .filter(account -> account.getAccountNumber() != null
                        && account.getAccountNumber().startsWith("MOCK-"))
                .toList();
        List<Account> desired = new ArrayList<>();
        for (AssetSeed seed : scenario.assets()) {
            // account_number를 accountType 기준으로 부여한다(#130).
            // 과거엔 시드 인덱스(MOCK-{userId}-{i+1}) 기반이라, 시나리오 자산 목록/순서가
            // 바뀐 레거시 데이터에서는 upsert 매칭 키(accountType)와 번호가 어긋났다.
            // 그 경우 한 계정은 신규 INSERT로, 다른 기존 계정은 같은 번호를 보유한 채
            // UPDATE로 처리되는데, Hibernate가 INSERT를 UPDATE보다 먼저 flush 하면서
            // account_number 유니크 충돌(500)이 발생했다. 번호를 타입 기준으로 고정하면
            // 매칭 키와 번호가 항상 일치해 재동기화가 멱등해지고 충돌이 원천 차단된다.
            Account account = new Account(user, seed.category(), seed.institutionName(),
                    "MOCK-" + userId + "-" + seed.category(), seed.amount(), true);
            account.updateDisplayNumber(generateDisplayNumber());
            desired.add(account);
        }
        List<Account> saved = upsertByKey(existing, desired, Account::getAccountType,
                (target, seed) -> {
                    target.updateMock(seed.getInstitutionName(),
                            seed.getAccountNumber(), seed.getDepositBalance());
                    if (target.getDisplayNumber() == null) {
                        target.updateDisplayNumber(seed.getDisplayNumber());
                    }
                },
                accountRepository::deleteAll, accountRepository::saveAll);

        LocalDate fifteenYearsAgo = LocalDate.now().minusYears(15);
        for (Account account : saved) {
            if ("IRP".equals(account.getAccountType())) {
                account.updateOpenedAt(fifteenYearsAgo);
                BigDecimal retirement = account.getDepositBalance()
                        .multiply(scenario.irpRetirementRatio())
                        .setScale(0, RoundingMode.HALF_UP);
                BigDecimal personal = account.getDepositBalance().subtract(retirement);
                account.linkIrpComposition(retirement, personal);
            } else if ("PENSION_SAVING".equals(account.getAccountType())) {
                account.updateOpenedAt(fifteenYearsAgo);
            }
        }
        return saved;
    }

    private String generateDisplayNumber() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format("%03d-%04d-%06d",
                random.nextInt(100, 1000),
                random.nextInt(1000, 10000),
                random.nextInt(100000, 1000000));
    }

    private List<Holding> saveHoldings(List<Account> accounts,
                                       List<HoldingSeed> etfSeeds,
                                       List<HoldingSeed> stockSeeds) {
        Account brokerage = accounts.stream()
                .filter(a -> "BROKERAGE".equals(a.getAccountType()))
                .findFirst().orElse(null);
        if (brokerage == null || (etfSeeds.isEmpty() && stockSeeds.isEmpty())) {
            return List.of();
        }

        // ETF: 화이트리스트 풀에서 매핑(무결성 유지 — 풀에 없는 티커면 throw)
        Map<String, Long> etfTickerToProductId;
        try {
            etfTickerToProductId = etfPoolProvider.getPool().stream()
                    .filter(info -> info.productId() != null)
                    .collect(Collectors.toMap(EtfInfo::ticker, EtfInfo::productId, (a, b) -> a));
        } catch (Exception e) {
            return List.of();
        }
        boolean allEtfMapped = etfSeeds.stream().allMatch(s -> etfTickerToProductId.containsKey(s.ticker()));
        if (!allEtfMapped) {
            throw new IllegalStateException("ETF 풀에 없는 티커가 HoldingSeed에 포함되어 있습니다.");
        }

        // STOCK: financial_product(product_type='STOCK')에서 ticker_code로 매핑.
        // stock_detail 카탈로그가 없는 환경(예: 단위 H2 컨텍스트)에서는 조회가 실패하므로
        // 개별주 시드를 건너뛴다(주식은 월급 집계에서 제외되어 추천 파이프라인엔 영향 없음).
        Map<String, Long> stockTickerToProductId = Map.of();
        if (!stockSeeds.isEmpty()) {
            try {
                stockTickerToProductId = holdingRepository.findStockProductIds(
                        stockSeeds.stream().map(HoldingSeed::ticker).toList()).stream()
                        .collect(Collectors.toMap(
                                StockTickerProductId::getTicker,
                                StockTickerProductId::getProductId,
                                (a, b) -> a));
            } catch (Exception e) {
                stockTickerToProductId = Map.of();
            }
            // 카탈로그가 존재(조회 성공·비어있지 않음)하는데 특정 티커만 없으면 시드 오타로 보고 fail-fast.
            if (!stockTickerToProductId.isEmpty()) {
                Map<String, Long> resolved = stockTickerToProductId;
                boolean allStockMapped = stockSeeds.stream().allMatch(s -> resolved.containsKey(s.ticker()));
                if (!allStockMapped) {
                    throw new IllegalStateException("stock_detail에 없는 티커가 주식 HoldingSeed에 포함되어 있습니다.");
                }
            }
        }

        List<Holding> desired = new ArrayList<>();
        for (HoldingSeed seed : etfSeeds) {
            desired.add(new Holding(brokerage, etfTickerToProductId.get(seed.ticker()),
                    seed.evaluationAmount(), seed.quantity()));
        }
        // 개별주는 평가액을 저장하지 않고 '수량 × 실시간 현재가'로 산출된다(AssetAggregator). 따라서
        // 시드 수량을 시세와 무관하게 박으면 표시 평가액이 의도와 크게 어긋난다(예: 삼성 40M 의도인데
        // 570주 × 33.9만 = 1.93억). 시드 시점에 실제 현재가를 조회해 '수량 = 목표평가액 ÷ 현재가'로 맞춘다.
        Map<String, Long> resolvedStockIds = stockTickerToProductId;
        List<Long> stockProductIds = stockSeeds.stream()
                .map(s -> resolvedStockIds.get(s.ticker()))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> stockPrices = safeFetchStockPrices(stockProductIds);
        for (HoldingSeed seed : stockSeeds) {
            Long stockProductId = resolvedStockIds.get(seed.ticker());
            if (stockProductId != null) {
                long price = stockPrices.getOrDefault(stockProductId, 0L);
                // 현재가 조회 불가(0)면 시드 수량으로 폴백(예: 단위 테스트·시세 미구독 환경).
                BigDecimal quantity = price > 0
                        ? seed.evaluationAmount().divide(BigDecimal.valueOf(price), 0, RoundingMode.HALF_UP)
                                .max(BigDecimal.ONE)
                        : seed.quantity();
                desired.add(new Holding(brokerage, stockProductId, null, quantity));
            }
        }
        // 재동기화 시 (account, productId) 기준 upsert — 기존 보유행을 재사용해 holdingId를 유지한다.
        // deleteAll+insert는 holdingId를 재발급해 월급 자산 제외(HOLDING_*)가 풀리는 문제(#207)를 유발.
        List<Holding> existing = holdingRepository.findByAccountIn(List.of(brokerage));
        return upsertByKey(existing, desired,
                h -> String.valueOf(h.getProductId()),
                (target, seed) -> target.updateMockValuation(seed.getEvaluationAmount(), seed.getQuantity()),
                holdingRepository::deleteAll, holdingRepository::saveAll);
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

    private SavedConnections saveConnections(User user, LocalDateTime syncedAt) {
        List<AssetConnection> desired = List.of(
                new AssetConnection(user, "신한은행", "BANK", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한투자증권", "SECURITIES", "CONNECTED", syncedAt),
                new AssetConnection(user, "국민연금공단", "PENSION", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한라이프", "INSURANCE", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한카드", "CARD", "CONNECTED", syncedAt),
                new AssetConnection(user, "신한은행", "LOAN", "CONNECTED", syncedAt)
        );

        // 재동기화는 mock 시드(신한 계열)만 추가/갱신한다(#223). 사용자가 마이페이지에서 직접
        // 연결한 기관(예: KB·카카오)은 desired에 없어도 절대 삭제하지 않는다 — upsertByKey의
        // '미매칭 행 삭제' 계약을 쓰면 수동 연결이 재동기화마다 사라진다(연결 테이블은 mock·수동
        // 행이 섞여 있어 holding처럼 전량 재생성할 수 없다). 시드는 (기관명, category) 복합키로
        // 매칭해 신한은행 BANK/LOAN 두 행을 각각 유지한다.
        List<AssetConnection> existing = assetConnectionRepository.findByUserUserId(user.getUserId());
        Map<String, AssetConnection> existingByKey = existing.stream()
                .collect(Collectors.toMap(
                        c -> connectionKey(c.getInstitutionName(), c.getCategory()),
                        c -> c, (a, b) -> a, LinkedHashMap::new));

        List<AssetConnection> result = new ArrayList<>(existing);
        List<AssetConnection> toSave = new ArrayList<>();
        for (AssetConnection seed : desired) {
            AssetConnection found = existingByKey.get(connectionKey(seed.getInstitutionName(), seed.getCategory()));
            if (found != null) {
                found.updateMock(seed.getInstitutionName(), seed.getLastSyncedAt());
                toSave.add(found);
            } else {
                toSave.add(seed);
                result.add(seed);
            }
        }
        List<AssetConnection> savedSeeds = assetConnectionRepository.saveAll(toSave);

        // generatedCounts.connections는 '이번 동기화로 생성/갱신한 시드 행 수'만 의미한다.
        // 반면 connectedInstitutionCount는 온보딩 카운트 == 마이페이지 카운트 불변식(#204)을 위해
        // 사용자 수동 연결까지 포함한 전체 CONNECTED 집합으로 센다 — 두 집합을 분리해 전달한다.
        List<AssetConnection> connectedRows = result.stream()
                .filter(c -> "CONNECTED".equals(c.getConnectionStatus()))
                .toList();
        return new SavedConnections(savedSeeds, connectedRows);
    }

    private static String connectionKey(String institutionName, String category) {
        return institutionName + "|" + category;
    }

    /** 재동기화 결과: generatedRows = 이번에 생성/갱신한 시드 행, connectedRows = 사용자 전체 CONNECTED 행. */
    private record SavedConnections(
            List<AssetConnection> generatedRows,
            List<AssetConnection> connectedRows
    ) {
    }

    private List<CashFlowEvent> buildMonthEvents(User user, LocalDate monthStart,
                                                  Scenario scenario, boolean recurring,
                                                  BigDecimal interestAmount, int interestDay) {
        String status = recurring ? "SCHEDULED" : "COMPLETED";
        YearMonth ym = YearMonth.from(monthStart);
        // 월 단위 공통 변동 계수: 그 달 이자·관리비·소비를 함께 흔들어 월 합계가 눈에 띄게 달라지게 한다.
        BigDecimal cashFactor = MonthlyVariation.cashFactor(ym);

        List<CashFlowEvent> events = new ArrayList<>();

        // 연금·보험·대출상환은 현실에서도 매달 고정이라 변동을 주지 않는다.
        if (Boolean.TRUE.equals(user.getNationalPensionReceiving())
                && scenario.monthlyPensionIncome().signum() > 0) {
            events.add(event(user, monthStart.withDayOfMonth(5), "PENSION", "국민연금 입금",
                    scenario.monthlyPensionIncome(), "INCOME", status, recurring));
        }
        // 금융수입(이자·배당 외 기타 금융소득)도 월별 변동 계수를 적용해 매달 같은 값이 되지 않게 한다.
        if (scenario.monthlyFinancialIncome() != null
                && scenario.monthlyFinancialIncome().signum() > 0) {
            BigDecimal monthFinancial = scenario.monthlyFinancialIncome()
                    .multiply(cashFactor).setScale(0, RoundingMode.HALF_UP);
            events.add(event(user, monthStart.withDayOfMonth(5), "FINANCIAL_INCOME", "금융수입",
                    monthFinancial, "INCOME", status, recurring));
        }
        // 예금 이자: 실제 계좌 잔고 × 금리 / 1200 계산값에 월별 계수를 적용해 매달 같은 값이 되지 않게 한다.
        // 지급일은 account.openedAt 기준. 배당은 보유 ETF 단일 출처라 여기서 시드하지 않는다(#216).
        if (interestAmount.signum() > 0) {
            BigDecimal monthInterest = interestAmount.multiply(cashFactor).setScale(0, RoundingMode.HALF_UP);
            int cappedDay = Math.min(interestDay, ym.lengthOfMonth());
            events.add(event(user, monthStart.withDayOfMonth(cappedDay), "INTEREST", "예금 이자",
                    monthInterest, "INCOME", status, recurring));
        }

        // 관리비는 계절(냉난방)에 따라 변동이 자연스러우므로 월별 계수를 적용한다.
        if (scenario.monthlyMaintenanceExpense().signum() > 0) {
            BigDecimal monthMaintenance = scenario.monthlyMaintenanceExpense()
                    .multiply(cashFactor).setScale(0, RoundingMode.HALF_UP);
            events.add(event(user, monthStart.withDayOfMonth(10), "MAINTENANCE", "아파트 관리비",
                    monthMaintenance, "EXPENSE", status, recurring));
        }
        if (scenario.monthlyInsurancePremium().signum() > 0) {
            events.add(event(user, monthStart.withDayOfMonth(15), "INSURANCE", "신한라이프 보험료",
                    scenario.monthlyInsurancePremium(), "EXPENSE", status, recurring));
        }
        if (scenario.monthlyLoanRepayment().signum() > 0) {
            events.add(event(user, monthStart.withDayOfMonth(27), "LOAN", "신한은행 대출상환",
                    scenario.monthlyLoanRepayment(), "EXPENSE", status, recurring));
        }

        // 소비 거래는 일회성 내역이므로 항상 비반복(COMPLETED)으로 시드한다.
        // 현재월 호출(recurring=true) 때 소비까지 recurring=true가 되면, 캘린더가 recurring 이벤트를
        // 이후 모든 달로 투영해 7·8·9월…에 같은 소비가 반복 표시된다(#177). 정기 수입/고정비만
        // recurring을 유지하고, 소비는 제 달에만 보이도록 한다.
        // 단, 현재달 안에서도 오늘보다 미래 날짜의 소비는 아직 발생하지 않은 내역이므로 PENDING으로
        // 시드한다. 과거 달은 today 이후 날짜가 존재할 수 없어 항상 COMPLETED로 남는다.
        LocalDate today = LocalDate.now();
        List<MockTransactionTemplates.TransactionTemplate> templates = scenario.transactions();
        for (int i = 0; i < templates.size(); i++) {
            MockTransactionTemplates.TransactionTemplate t = templates.get(i);
            int day = TEMPLATE_DAYS[i % TEMPLATE_DAYS.length];
            BigDecimal amount = applyVariation(BigDecimal.valueOf(t.baseAmount()), ym, i);
            LocalDate eventDate = monthStart.withDayOfMonth(day);
            String consumptionStatus = eventDate.isAfter(today) ? "PENDING" : "COMPLETED";
            events.add(event(user, eventDate, t.eventType(), t.title(),
                    amount, "EXPENSE", consumptionStatus, false));
        }

        return events;
    }

    private List<CashFlowEvent> buildMonthStockEvents(User user, LocalDate monthStart, Scenario scenario) {
        List<MockTransactionTemplates.TransactionTemplate> trades = scenario.stockTrades();
        YearMonth ym = YearMonth.from(monthStart);
        LocalDate today = LocalDate.now();
        List<CashFlowEvent> events = new ArrayList<>();
        for (int i = 0; i < trades.size(); i++) {
            MockTransactionTemplates.TransactionTemplate t = trades.get(i);
            int day = STOCK_TRADE_DAYS[i % STOCK_TRADE_DAYS.length];
            BigDecimal amount = applyVariation(BigDecimal.valueOf(t.baseAmount()), ym, i);
            String flowType = "STOCK_BUY".equals(t.eventType()) ? "EXPENSE" : "INCOME";
            LocalDate eventDate = monthStart.withDayOfMonth(day);
            String tradeStatus = eventDate.isAfter(today) ? "PENDING" : "COMPLETED";
            events.add(event(user, eventDate, t.eventType(), t.title(),
                    amount, flowType, tradeStatus, false));
        }
        return events;
    }

    /**
     * 거래 금액에 변동을 준다. 핵심은 <b>월 단위 공통 계수</b>(그 달 전체를 함께 흔듦)에
     * 거래별 미세 노이즈를 곱하는 것. 과거엔 거래별 노이즈만 줘서 합산하면 1.0 근처로 상쇄돼
     * 월 합계가 거의 안 변했다(5월·4월 소비 총액이 685원밖에 차이 안 남).
     */
    private BigDecimal applyVariation(BigDecimal baseAmount, YearMonth month, int templateIndex) {
        BigDecimal monthFactor = MonthlyVariation.cashFactor(month);                       // 0.90 ~ 1.10
        BigDecimal itemFactor = BigDecimal.valueOf(
                94 + ((month.getMonthValue() * 7 + templateIndex * 3) % 13), 2);            // 0.94 ~ 1.06
        return baseAmount.multiply(monthFactor).multiply(itemFactor).setScale(0, RoundingMode.HALF_UP);
    }

    private List<CashFlowEvent> saveCashflowEvents(User user, Scenario scenario, List<Account> accounts) {
        List<CashFlowEvent> existing = cashFlowEventRepository.findByUserUserId(user.getUserId()).stream()
                .filter(e -> "MYDATA_MOCK".equals(e.getSource()))
                .toList();
        cashFlowEventRepository.deleteAll(existing);

        // 예금 이자 금액과 지급일을 schedule/income-analysis와 동일한 기준으로 계산한다.
        // - 금액: balance × rate / 1200 (AssetIncomeService·AssetScheduleService와 동일 공식)
        // - 지급일: account.openedAt의 일(日) (AssetScheduleService.addDepositInterestEvents와 동일)
        // saveAssets 결과를 그대로 사용해 DB 재조회를 피한다(linkDepositProductId가 productId를 인-플레이스 설정).
        BigDecimal interestAmount = computeDepositInterest(accounts);
        int interestDay = resolveInterestDay(accounts);

        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        List<CashFlowEvent> events = new ArrayList<>();
        events.addAll(buildMonthEvents(user, currentMonth, scenario, true, interestAmount, interestDay));
        events.addAll(buildMonthStockEvents(user, currentMonth, scenario));
        // 현재월 + 과거 11개월 = 약 1년치 소비·수입·매매 내역을 시드한다.
        for (int i = 1; i <= 11; i++) {
            LocalDate pastMonth = currentMonth.minusMonths(i);
            events.addAll(buildMonthEvents(user, pastMonth, scenario, false, interestAmount, interestDay));
            events.addAll(buildMonthStockEvents(user, pastMonth, scenario));
        }
        return cashFlowEventRepository.saveAll(events);
    }

    private BigDecimal computeDepositInterest(List<Account> accounts) {
        List<Account> depositAccounts = accounts.stream()
                .filter(a -> "DEPOSIT".equals(a.getAccountType()) && a.getProductId() != null)
                .toList();
        if (depositAccounts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> productIds = depositAccounts.stream().map(Account::getProductId).toList();
        Map<Long, DepositDetailItem> details = depositDetailClient.fetchDepositDetails(productIds);
        return depositAccounts.stream()
                .filter(a -> details.containsKey(a.getProductId()))
                .map(a -> {
                    DepositDetailItem detail = details.get(a.getProductId());
                    if (detail.interestRate() == null) return BigDecimal.ZERO;
                    BigDecimal balance = a.getDepositBalance() == null ? BigDecimal.ZERO : a.getDepositBalance();
                    return balance.multiply(detail.interestRate())
                            .divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP)
                            .setScale(0, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int resolveInterestDay(List<Account> accounts) {
        return accounts.stream()
                .filter(a -> "DEPOSIT".equals(a.getAccountType()) && a.getOpenedAt() != null)
                .findFirst()
                .map(a -> a.getOpenedAt().getDayOfMonth())
                .orElse(20);
    }

    /**
     * 온보딩 산출물(프로필 + 목표) 시드. 마이데이터로 알 수 없는 직접입력값이므로
     * force=false(실사용자 동기화)일 때는 비어있는 경우에만 채워 실제 온보딩을 덮지 않는다.
     * force=true(개발 시드)일 때는 시나리오 값으로 강제 세팅한다.
     */
    private void saveOnboarding(User user, Scenario scenario, boolean force) {
        LocalDateTime now = LocalDateTime.now();
        if (force || user.getAge() == null) {
            user.updateProfile(scenario.age(), scenario.retired(),
                    scenario.nationalPensionReceiving(), now);
        }
        Optional<UserGoal> existing = userGoalRepository.findByUserUserId(user.getUserId());
        if (existing.isPresent()) {
            if (force) {
                existing.get().updateMock(scenario.targetLivingCost(), scenario.expectedMedicalCost(), now);
                userGoalRepository.save(existing.get());
            }
        } else {
            userGoalRepository.save(new UserGoal(user,
                    scenario.targetLivingCost(), scenario.expectedMedicalCost(), now));
        }
    }

    /**
     * 월급만들기 설문(Q1~Q3) 시드. 추천 파이프라인 필수 입력이다.
     * force=false면 기존 응답이 있을 때 건드리지 않는다(사용자 실제 응답 보존).
     */
    private void saveSurvey(User user, Scenario scenario, boolean force) {
        List<SurveyResponse> existing =
                surveyResponseRepository.findByUserAndQuestionCodeIn(user, SALARY_SURVEY_CODES);
        // 실사용자(force=false)는 Q1~Q3가 모두 존재할 때만 보존하고 끝낸다 — 일부만 있는 불완전 상태는 다시 채운다.
        if (!force && existing.size() == SALARY_SURVEY_CODES.size()) {
            return;
        }
        if (!existing.isEmpty()) {
            surveyResponseRepository.deleteAll(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        surveyResponseRepository.saveAll(List.of(
                surveyResponse(user, "MONTHLY_SALARY_Q1", scenario.q1(), now),
                surveyResponse(user, "MONTHLY_SALARY_Q2", scenario.q2(), now),
                surveyResponse(user, "MONTHLY_SALARY_Q3", scenario.q3(), now)
        ));
    }

    private SurveyResponse surveyResponse(User user, String questionCode, int answerValue, LocalDateTime now) {
        return SurveyResponse.builder()
                .user(user)
                .questionCode(questionCode)
                .answerValue(String.valueOf(answerValue))
                .answeredAt(now)
                .build();
    }

    /**
     * 마이데이터 거래내역 시드. BROKERAGE 계좌의 보유종목별로 과거 12개월 적립식 매수 내역을 만든다.
     * 거래내역은 mock BROKERAGE 계좌 전용이므로, 이미 존재하면(재동기화·실거래 이후) 건드리지 않아
     * 멱등하고 사용자 실거래를 덮지 않는다.
     */
    private void saveTradeHistory(List<Account> accounts, List<Holding> holdings) {
        Account brokerage = accounts.stream()
                .filter(a -> "BROKERAGE".equals(a.getAccountType()))
                .findFirst().orElse(null);
        if (brokerage == null || brokerage.getAccountId() == null || holdings.isEmpty()) {
            return;
        }
        if (tradeHistoryRepository.existsByAccountAccountId(brokerage.getAccountId())) {
            return;
        }
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        List<TradeHistory> rows = new ArrayList<>();
        for (Holding holding : holdings) {
            BigDecimal quantity = holding.getQuantity();
            if (quantity == null || quantity.signum() <= 0) {
                continue;
            }
            BigDecimal unitPrice = nz(holding.getEvaluationAmount())
                    .divide(quantity, 2, RoundingMode.HALF_UP);
            BigDecimal perMonthQty = quantity.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            if (unitPrice.signum() <= 0 || perMonthQty.signum() <= 0) {
                continue;
            }
            // 11개월은 균등 수량, 마지막(최근, i==0) 달이 잔여를 흡수해 12개월 합계가 보유수량과 정확히 일치하게 한다.
            // (12로 나누어떨어지지 않으면 반올림 누적으로 합계가 어긋나는 문제 방지)
            BigDecimal lastMonthQty = quantity.subtract(perMonthQty.multiply(BigDecimal.valueOf(11)));
            for (int i = 11; i >= 0; i--) {
                LocalDateTime tradedAt = currentMonth.minusMonths(i).withDayOfMonth(12).atTime(10, 0);
                BigDecimal monthQty = (i == 0) ? lastMonthQty : perMonthQty;
                rows.add(TradeHistory.ofMock(brokerage, holding.getProductId(),
                        "BUY", monthQty, unitPrice, tradedAt));
            }
        }
        if (!rows.isEmpty()) {
            tradeHistoryRepository.saveAll(rows);
        }
    }

    /**
     * 월별 총자산 스냅샷(과거 12개월) 백필 — "자산 변화"의 정합성 핵심.
     *
     * <p>월간 리포트는 (그 달 스냅샷 − 전월 스냅샷)으로 자산 변화를 보여준다. 임의 성장곡선이 아니라
     * 그 달의 실제 변화량에서 역산한다:
     * <pre>변화량(월) = 순현금흐름 + 배당 + 시세변동
     *   순현금흐름 = (연금 + 이자) − (소비 + 관리비 + 보험 + 대출상환)
     *   배당      = 보유 ETF 월배당 × 배당 월별계수
     *   시세변동   = 보유 평가액 × 시세 월수익률(±)   ← 자산이 늘고 주는 출처</pre>
     * 주식 매수/매도는 현금↔보유종목 전환이라 순자산 중립 → 순현금흐름에서 제외한다.
     * 현재 총자산에서 최근 달부터 변화량을 한 달씩 빼며 거슬러 올라가므로,
     * {@code snapshot(M-1) = snapshot(M) − 변화량(M)} 이 성립한다. 배당·시세 계수는 결정적
     * (MonthlyVariation)이라 리포트 읽기 경로와 같은 값이 나온다. 현재월 스냅샷은 스냅샷
     * 스케줄러가 소유하므로 건드리지 않는다(과거 12개월만 쓴다).
     */
    private void saveMonthlyReportSnapshots(User user, Long userId, BigDecimal currentTotal,
                                            BigDecimal holdingsValuation, List<CashFlowEvent> events) {
        if (currentTotal == null || currentTotal.signum() <= 0) {
            return;
        }
        Map<YearMonth, BigDecimal> netCashFlowByMonth = netCashFlowByMonth(events);
        // 배당은 시드 이벤트가 아니라 보유 ETF 단일 출처(#216)다. 보유수량은 고정이지만,
        // 실제 분배는 달마다 들쭉날쭉하므로 월별 계수를 적용한다(리포트와 같은 값 — MonthlyVariation).
        BigDecimal baseDividend = nz(etfDividendCalculator.monthlyDividend(userId));
        BigDecimal holdingsBase = nz(holdingsValuation);

        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        BigDecimal running = currentTotal;
        for (int i = 1; i <= 12; i++) {
            // i번째 거슬러가기: (i-1)개월 전 달의 변화량만큼 빼면 i개월 전 말 잔액이 된다.
            YearMonth changedMonth = YearMonth.from(currentMonth.minusMonths(i - 1));
            BigDecimal dividend = baseDividend.multiply(MonthlyVariation.dividendFactor(changedMonth))
                    .setScale(0, RoundingMode.HALF_UP);
            // 시세변동(평가손익): 순현금흐름만으론 자산이 단조 증가만 하므로, 이걸로 늘고 줄게 만든다.
            BigDecimal marketChange = holdingsBase.multiply(MonthlyVariation.marketReturn(changedMonth))
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal delta = netCashFlowByMonth.getOrDefault(changedMonth, BigDecimal.ZERO)
                    .add(dividend)
                    .add(marketChange);
            running = running.subtract(delta);

            String month = YearMonth.from(currentMonth.minusMonths(i)).toString();
            BigDecimal total = running;
            monthlyReportRepository.findByUserUserIdAndCurrentMonth(userId, month)
                    .ifPresentOrElse(
                            existing -> existing.updateTotalAsset(total),
                            () -> monthlyReportRepository.save(MonthlyReport.snapshot(user, month, total))
                    );
        }
    }

    /**
     * 월별 순현금흐름 = Σ(INCOME) − Σ(EXPENSE). 단, 주식 매수/매도(STOCK_BUY/STOCK_SELL)는
     * 현금↔보유종목 전환이라 순자산에 영향이 없으므로 제외한다.
     */
    private Map<YearMonth, BigDecimal> netCashFlowByMonth(List<CashFlowEvent> events) {
        Map<YearMonth, BigDecimal> netByMonth = new LinkedHashMap<>();
        for (CashFlowEvent event : events) {
            if (event.getEventDate() == null
                    || "STOCK_BUY".equals(event.getEventType())
                    || "STOCK_SELL".equals(event.getEventType())) {
                continue;
            }
            YearMonth month = YearMonth.from(event.getEventDate());
            BigDecimal amount = nz(event.getAmount());
            BigDecimal signed = "INCOME".equals(event.getFlowType()) ? amount : amount.negate();
            netByMonth.merge(month, signed, BigDecimal::add);
        }
        return netByMonth;
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
                                BigDecimal amount, String flowType, String status, boolean recurring) {
        return new CashFlowEvent(user, date, type, title, amount, flowType, status, recurring, "MYDATA_MOCK");
    }

    /**
     * 요약은 시나리오가 아니라 실제 저장된 계좌·보유종목 기준으로 계산한다.
     * {@code saveHoldings}가 증권계좌 부재나 ETF 풀 조회 실패로 빈 리스트를 반환하면
     * DB엔 종목이 없으므로, 시나리오 평가액을 더하면 응답과 실제 저장이 어긋난다.
     */
    private AssetSummaryResponse createAssetSummary(List<Account> accounts, BigDecimal debtBalance,
                                                    BigDecimal holdingsTotal) {
        Map<String, BigDecimal> grouped = new LinkedHashMap<>();
        accounts.forEach(account ->
                grouped.merge(account.getAccountType(), nz(account.getDepositBalance()), BigDecimal::add));

        // 예수금만 계약: 증권 종목 평가액은 BROKERAGE 예수금(deposit_balance)에 없으므로 따로 더한다.
        // 개별주는 평가액=null(현재가 기반)이라 AssetAggregator와 동일하게 '수량 × 현재가'로 평가해야
        // 응답·스냅샷 총자산이 실제 표시값과 일치한다(이게 빠지면 자산변화에 거대 점프가 생긴다).
        // holdingsTotal은 호출부에서 holdingsMarketValue로 1회 계산해 전달(스냅샷 베이스와 동일 값).
        if (holdingsTotal.signum() > 0) {
            grouped.merge("BROKERAGE", holdingsTotal, BigDecimal::add);
        }

        List<AssetGroupSummary> groups = grouped.entrySet().stream()
                .map(entry -> new AssetGroupSummary(entry.getKey(), entry.getValue()))
                .toList();

        BigDecimal totalAsset = grouped.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AssetSummaryResponse(
                totalAsset,
                debtBalance,
                totalAsset.subtract(debtBalance),
                groups
        );
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 보유 종목 시가총액 = ETF(저장 평가액) + 개별주(수량 × 실시간 현재가).
     * 개별주는 평가액을 저장하지 않으므로 AssetAggregator와 동일하게 현재가로 산출한다.
     * 현재가 조회 실패는 fail-open(0) — 시드/요약이 외부 장애로 깨지지 않게 한다.
     */
    private BigDecimal holdingsMarketValue(List<Holding> holdings) {
        BigDecimal nonStock = holdings.stream()
                .map(Holding::getEvaluationAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> stockProductIds = holdings.stream()
                .filter(h -> h.getEvaluationAmount() == null)
                .map(Holding::getProductId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> prices = safeFetchStockPrices(stockProductIds);
        BigDecimal stock = holdings.stream()
                .filter(h -> h.getEvaluationAmount() == null)
                .map(h -> nz(h.getQuantity())
                        .multiply(BigDecimal.valueOf(prices.getOrDefault(h.getProductId(), 0L))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return nonStock.add(stock);
    }

    private Map<Long, Long> safeFetchStockPrices(List<Long> stockProductIds) {
        if (stockProductIds.isEmpty()) {
            return Map.of();
        }
        try {
            return productBatchClient.fetchStockPrices(stockProductIds);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private Scenario scenarioOf(MockType mockType) {
        return switch (mockType) {
            case NEED_IMPROVEMENT -> new Scenario(
                    InvestmentPropensity.ACTIVE,
                    List.of(
                            asset("CMA", "신한투자증권", 6_000_000),
                            asset("DEPOSIT", "신한은행", 12_000_000),
                            // 예수금만 계약: BROKERAGE 잔액=예수금(현금). 종목 가치(8천만)는 holdings가 보유 → 예수금 0(완전투자).
                            asset("BROKERAGE", "신한투자증권", 0),
                            asset("IRP", "신한투자증권", 25_000_000)
                    ),
                    List.of(
                            holding("433330", 40_000_000, 2_000),  // SOL 미국S&P500
                            holding("476030", 40_000_000, 2_500)   // SOL 미국나스닥100
                    ),
                    // 개별주(ACTIVE 공격투자자 — 多). 월급재료엔 안 잡히고 순자산/성장블록에만 잡힘.
                    List.of(
                            stock("005930", 12_000_000, 180),  // 삼성전자
                            stock("000660", 8_000_000, 40),    // SK하이닉스
                            stock("005380", 6_000_000, 25)     // 현대차
                    ),
                    money(75_000_000), money(650_000), new BigDecimal("4.80"),
                    money(5_400_000), money(600_000), money(104_000),
                    money(250_000), money(180_000), MockTransactionTemplates.NEED_IMPROVEMENT,
                    MockTransactionTemplates.NEED_IMPROVEMENT_STOCKS,
                    new BigDecimal("0.6"),
                    // 개선필요(공격투자·고소비) — 60세 은퇴·연금수령, 생활비 350만·의료 60만, 위험선호 설문, 월성장 0.6%
                    60, true, true, money(3_500_000), money(600_000), 3, 1, 1
            );
            case NEED_COMPLEMENT -> new Scenario(
                    InvestmentPropensity.NEUTRAL,
                    List.of(
                            asset("CMA", "신한투자증권", 18_000_000),
                            asset("DEPOSIT", "신한은행", 45_000_000),
                            // 예수금만 계약: 종목 가치(5천5백만)는 holdings 보유 → BROKERAGE 예수금 0.
                            asset("BROKERAGE", "신한투자증권", 0),
                            asset("IRP", "신한투자증권", 65_000_000),
                            asset("PENSION_SAVING", "신한투자증권", 25_000_000)
                    ),
                    List.of(
                            holding("433330", 30_000_000, 1_500),  // SOL 미국S&P500
                            holding("292500", 25_000_000, 2_500)   // SOL KRX300
                    ),
                    // 개별주(NEUTRAL — 2종)
                    List.of(
                            stock("005930", 8_000_000, 120),   // 삼성전자
                            stock("373220", 5_000_000, 12)     // LG에너지솔루션
                    ),
                    money(30_000_000), money(300_000), new BigDecimal("4.10"),
                    money(4_200_000), money(1_150_000), money(148_000),
                    money(200_000), money(180_000), MockTransactionTemplates.NEED_COMPLEMENT,
                    MockTransactionTemplates.NEED_COMPLEMENT_STOCKS,
                    new BigDecimal("0.6"),
                    // 보완필요(중립투자) — 64세 은퇴·연금수령, 생활비 300만·의료 50만, 중립 설문, 월성장 0.4%
                    64, true, true, money(3_000_000), money(500_000), 2, 1, 1
            );
            case STABLE -> new Scenario(
                    InvestmentPropensity.STABLE,
                    List.of(
                            asset("CMA", "신한투자증권", 35_000_000),
                            asset("DEPOSIT", "신한은행", 90_000_000),
                            // 예수금만 계약: 종목 가치(1억3천만)는 holdings 보유 → BROKERAGE 예수금 0.
                            asset("BROKERAGE", "신한투자증권", 0),
                            asset("IRP", "신한투자증권", 120_000_000),
                            asset("PENSION_SAVING", "신한투자증권", 60_000_000)
                    ),
                    List.of(
                            holding("446720", 55_000_000, 5_000),  // SOL 미국배당다우존스
                            holding("438560", 45_000_000, 400),    // SOL 국고채3년
                            holding("433330", 30_000_000, 1_500)   // SOL 미국S&P500
                    ),
                    // 개별주(STABLE 안정형 — 少, 1종)
                    List.of(
                            stock("005930", 5_000_000, 75)     // 삼성전자
                    ),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    // medicalReserve 9,000,000 → 의료대비 25.7개월(>=24)로 STABLE 등급(80점) 충족.
                    // 6,000,000이면 17.1개월(11점)에 그쳐 총 79점으로 STABLE 문턱에서 1점 부족했다.
                    money(9_000_000), money(2_000_000), money(464_000),
                    money(180_000), money(180_000), MockTransactionTemplates.STABLE,
                    MockTransactionTemplates.STABLE_STOCKS,
                    new BigDecimal("0.6"),
                    // 안정(안정형) — 68세 은퇴·연금수령, 생활비 250만·의료 40만, 안정 설문, 월성장 0.3%
                    68, true, true, money(2_500_000), money(400_000), 1, 1, 1
            );
            case SALARY_DEMO -> new Scenario(
                    InvestmentPropensity.AGGRESSIVE,
                    List.of(
                            asset("CMA", "신한투자증권", 50_000_000),
                            asset("DEPOSIT", "신한은행", 0),
                            // 예수금만 계약: 종목 가치는 holdings 보유 → BROKERAGE 예수금은 자유현금 5천만.
                            asset("BROKERAGE", "신한투자증권", 50_000_000),
                            asset("IRP", "신한투자증권", 200_000_000),
                            asset("PENSION_SAVING", "신한투자증권", 80_000_000)
                    ),
                    List.of(
                            holding("433330", 150_000_000, 7_500),   // SOL 미국S&P500
                            holding("446720", 150_000_000, 12_500)   // SOL 미국배당다우존스
                    ),
                    List.of(
                            stock("005930", 20_000_000, 300)         // 삼성전자(월급 재료 제외, 순자산만)
                    ),
                    money(0), money(0), BigDecimal.ZERO,
                    money(9_000_000), money(800_000), money(0),
                    money(150_000), money(200_000), MockTransactionTemplates.NEED_IMPROVEMENT,
                    MockTransactionTemplates.NEED_IMPROVEMENT_STOCKS,
                    new BigDecimal("0.6"),
                    // Target X: 운용총자산 6.8억·연금저축 2.8억·가용 4.0억·바닥 3.1억·여유분 0.9억 → 과충당 NORMAL.
                    // 연금 비중 확대(IRP 2억·PENSION_SAVING 8천만)로 q3 소진비율 스윙(0.3↔1.0) 확대.
                    // 생활비 하향(250만→185만)으로 바닥자산↓ → 여유분(소진레버)↑·바닥이자↓ → q3 스윙 20%+ 확보.
                    58, true, true, money(1_850_000), money(500_000), 2, 1, 1
            );
            // ── 현실 케이스 확장(realistic) ──
            case GROWTH_CONCENTRATED -> new Scenario(
                    InvestmentPropensity.ACTIVE,
                    List.of(
                            asset("CMA", "신한은행", 10_000_000),
                            asset("DEPOSIT", "신한은행", 20_000_000),
                            asset("BROKERAGE", "신한투자증권", 0)
                    ),
                    // ETF는 최소(쏠림 강조) — 1종
                    List.of(
                            holding("433330", 30_000_000, 1_500)   // SOL 미국S&P500
                    ),
                    // 개별주 6종 1.4억(70% 쏠림) — 투자검진 성장자산 쏠림·배당공백·역할불균형 시연
                    List.of(
                            stock("005930", 40_000_000, 570),   // 삼성전자
                            stock("000660", 30_000_000, 150),   // SK하이닉스
                            stock("005380", 25_000_000, 100),   // 현대차
                            stock("373220", 20_000_000, 50),    // LG에너지솔루션
                            stock("035420", 15_000_000, 80),    // NAVER
                            stock("035720", 10_000_000, 250)    // 카카오
                    ),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    money(5_000_000), money(900_000), money(0),
                    money(150_000), money(180_000), MockTransactionTemplates.NEED_IMPROVEMENT,
                    MockTransactionTemplates.NEED_IMPROVEMENT_STOCKS,
                    new BigDecimal("0.6"),
                    // 개별주 몰빵 — 60세 은퇴·연금수령, 생활비 300만·의료 50만, 위험선호 설문
                    60, true, true, money(3_000_000), money(500_000), 3, 1, 1
            );
            case PRE_RETIREMENT -> new Scenario(
                    InvestmentPropensity.NEUTRAL,
                    List.of(
                            asset("DEPOSIT", "신한은행", 100_000_000),
                            asset("BROKERAGE", "신한투자증권", 0),
                            asset("IRP", "신한투자증권", 100_000_000)
                    ),
                    List.of(
                            holding("433330", 50_000_000, 2_500),  // SOL 미국S&P500
                            holding("446720", 50_000_000, 4_500)   // SOL 미국배당다우존스
                    ),
                    List.of(),
                    money(100_000_000), money(500_000), new BigDecimal("3.50"),
                    money(4_000_000), money(1_200_000), money(0),
                    money(150_000), money(200_000), MockTransactionTemplates.NEED_COMPLEMENT,
                    List.of(),
                    new BigDecimal("0.6"),
                    // 은퇴 임박 — 59세 미은퇴·국민연금 미수령(기대 120만), 생활비 350만·의료 50만, 중립 설문
                    59, false, false, money(3_500_000), money(500_000), 2, 1, 1
            );
            case STRUCTURAL_SHORTAGE -> new Scenario(
                    InvestmentPropensity.STABLE,
                    List.of(
                            asset("DEPOSIT", "신한은행", 40_000_000),
                            asset("BROKERAGE", "신한투자증권", 0)
                    ),
                    List.of(
                            holding("446720", 12_000_000, 1_000),  // SOL 미국배당다우존스
                            holding("438560", 8_000_000, 70)       // SOL 국고채3년
                    ),
                    List.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    money(3_000_000), money(700_000), money(0),
                    money(100_000), money(150_000), MockTransactionTemplates.STABLE,
                    List.of(),
                    new BigDecimal("0.6"),
                    // 구조적 부족 — 67세 은퇴·연금수령(70만), 생활비 200만·의료 40만 → 충당률<100%, 안정 설문
                    67, true, true, money(2_000_000), money(400_000), 1, 1, 1
            );
            case PENSION_SUFFICIENT -> new Scenario(
                    InvestmentPropensity.STABLE_SEEKING,
                    List.of(
                            asset("CMA", "신한은행", 30_000_000),
                            asset("DEPOSIT", "신한은행", 90_000_000),
                            asset("BROKERAGE", "신한투자증권", 0),
                            asset("IRP", "신한투자증권", 80_000_000),
                            asset("PENSION_SAVING", "신한투자증권", 40_000_000)
                    ),
                    List.of(
                            holding("446720", 30_000_000, 2_500),  // SOL 미국배당다우존스
                            holding("438560", 20_000_000, 180),    // SOL 국고채3년
                            holding("433330", 10_000_000, 500)     // SOL 미국S&P500
                    ),
                    List.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    money(7_000_000), money(1_800_000), money(0),
                    money(180_000), money(180_000), MockTransactionTemplates.STABLE,
                    List.of(),
                    new BigDecimal("0.6"),
                    // 연금 충분 여유 — 70세 은퇴·연금수령(180만)으로 생활비(200만) 충당, 의료 40만, 안정추구 설문
                    70, true, true, money(2_000_000), money(400_000), 1, 2, 1
            );
            case HIGH_DEBT -> new Scenario(
                    InvestmentPropensity.ACTIVE,
                    List.of(
                            asset("CMA", "신한은행", 10_000_000),
                            asset("DEPOSIT", "신한은행", 10_000_000),
                            asset("BROKERAGE", "신한투자증권", 0)
                    ),
                    List.of(
                            holding("433330", 12_000_000, 600),    // SOL 미국S&P500
                            holding("476030", 8_000_000, 400)      // SOL 미국나스닥100
                    ),
                    List.of(),
                    money(200_000_000), money(1_200_000), new BigDecimal("5.50"),
                    money(2_000_000), money(1_000_000), money(0),
                    money(120_000), money(180_000), MockTransactionTemplates.NEED_IMPROVEMENT,
                    List.of(),
                    new BigDecimal("0.6"),
                    // 고부채 위기 — 58세 미은퇴·국민연금 미수령(기대 100만), 부채 2억, 생활비 300만·의료 50만, 위험선호 설문
                    58, false, false, money(3_000_000), money(500_000), 3, 1, 1
            );
            case PRIVATE_PENSION_RICH -> new Scenario(
                    InvestmentPropensity.NEUTRAL,
                    List.of(
                            asset("CMA", "신한은행", 20_000_000),
                            asset("DEPOSIT", "신한은행", 10_000_000),
                            asset("BROKERAGE", "신한투자증권", 0),
                            asset("IRP", "신한투자증권", 120_000_000),
                            asset("PENSION_SAVING", "신한투자증권", 80_000_000)
                    ),
                    List.of(
                            holding("446720", 12_000_000, 1_000),  // SOL 미국배당다우존스
                            holding("433330", 8_000_000, 400)      // SOL 미국S&P500
                    ),
                    List.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    money(5_000_000), money(1_200_000), money(0),
                    money(150_000), money(180_000), MockTransactionTemplates.NEED_COMPLEMENT,
                    List.of(),
                    new BigDecimal("0.6"),
                    // 사적연금 빵빵 — 63세 은퇴·연금수령(120만), IRP·연금저축 비중↑, 생활비 250만·의료 40만, 중립 설문
                    63, true, true, money(2_500_000), money(400_000), 2, 1, 1
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

    // 개별주 시드. 구조는 holding과 동일하지만, 화이트리스트 ETF가 아닌
    // financial_product(product_type='STOCK')로 매핑된다(saveHoldings 참조).
    private static HoldingSeed stock(String ticker, long amount, long quantity) {
        return holding(ticker, amount, quantity);
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount);
    }

    private record AssetSeed(String category, String institutionName, BigDecimal amount) {
    }

    private record HoldingSeed(String ticker, BigDecimal evaluationAmount, BigDecimal quantity) {
    }

    private record Scenario(
            InvestmentPropensity propensity,
            List<AssetSeed> assets,
            List<HoldingSeed> holdings,
            List<HoldingSeed> stocks,
            BigDecimal debtBalance,
            BigDecimal monthlyLoanRepayment,
            BigDecimal loanInterestRate,
            BigDecimal medicalReserve,
            BigDecimal monthlyPensionIncome,
            BigDecimal monthlyFinancialIncome,
            BigDecimal monthlyInsurancePremium,
            BigDecimal monthlyMaintenanceExpense,
            List<MockTransactionTemplates.TransactionTemplate> transactions,
            List<MockTransactionTemplates.TransactionTemplate> stockTrades,
            BigDecimal irpRetirementRatio,
            // ── 온보딩·설문·자산변화 페르소나 입력(#256) ──
            int age,
            boolean retired,
            boolean nationalPensionReceiving,
            BigDecimal targetLivingCost,
            BigDecimal expectedMedicalCost,
            int q1,
            int q2,
            int q3
    ) {
    }
}
