package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.MockAssetResponse;
import com.sol.user.asset.type.MockType;
import com.sol.user.assetconnection.entity.AssetConnection;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.cashflow.MonthlyVariation;
import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.dto.StockTickerProductId;
import com.sol.user.holding.entity.Holding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.holding.service.EtfDividendCalculator;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.report.entity.MonthlyReport;
import com.sol.user.report.repository.MonthlyReportRepository;
import com.sol.user.stability.service.LifeStabilityService;
import com.sol.user.survey.repository.SurveyResponseRepository;
import com.sol.user.trade.repository.TradeHistoryRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetMockServiceTest {

    @Mock UserRepository userRepository;
    @Mock AccountRepository accountRepository;
    @Mock PensionRepository pensionRepository;
    @Mock CashFlowEventRepository cashFlowEventRepository;
    @Mock AssetConnectionRepository assetConnectionRepository;
    @Mock DebtRepository debtRepository;
    @Mock InsurancePolicyRepository insurancePolicyRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock EtfPoolProvider etfPoolProvider;
    @Mock LifeStabilityService lifeStabilityService;
    @Mock DepositDetailClient depositDetailClient;
    @Mock UserGoalRepository userGoalRepository;
    @Mock SurveyResponseRepository surveyResponseRepository;
    @Mock TradeHistoryRepository tradeHistoryRepository;
    @Mock MonthlyReportRepository monthlyReportRepository;
    @Mock EtfDividendCalculator etfDividendCalculator;

    @InjectMocks AssetMockService assetMockService;

    @ParameterizedTest
    @MethodSource("scenarios")
    void createsScenarioWithSpecifiedSummary(MockType mockType, long totalAsset,
                                             long totalDebt, long netAsset, int holdingCount,
                                             InvestmentPropensity expectedPropensity,
                                             int expectedCashflowCount) {
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        MockAssetResponse response = assetMockService.create(1L, mockType);

        assertThat(response.assetSummary().totalAsset()).isEqualByComparingTo(BigDecimal.valueOf(totalAsset));
        assertThat(response.assetSummary().totalDebt()).isEqualByComparingTo(BigDecimal.valueOf(totalDebt));
        assertThat(response.assetSummary().netAsset()).isEqualByComparingTo(BigDecimal.valueOf(netAsset));
        assertThat(response.generatedCounts().connections()).isEqualTo(6);
        // 연결 행 6개지만 신한은행 BANK+LOAN이 1곳으로 합쳐져 기관명 distinct는 5 (#204)
        assertThat(response.connectedInstitutionCount()).isEqualTo(5);
        assertThat(response.generatedCounts().cashflowEvents()).isEqualTo(expectedCashflowCount);
        assertThat(response.generatedCounts().holdings()).isEqualTo(holdingCount);
        // 시나리오별 투자성향(KYC 목업)이 유저에 시드된다 — #118 권유가능등급 필터의 입력
        verify(user).assignInvestmentPropensity(expectedPropensity);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void createsBrokerageHoldingsWithEvaluationAmountAndQuantity() {
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.create(1L, MockType.NEED_IMPROVEMENT);

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(holdingRepository).saveAll(captor.capture());
        List<Holding> holdings = toList((Iterable<Holding>) captor.getValue());

        assertThat(holdings)
                .extracting(Holding::getProductId, Holding::getEvaluationAmount, Holding::getQuantity)
                .containsExactly(
                        // ETF(화이트리스트): evaluationAmount 저장
                        tuple(1001L, new BigDecimal("40000000"), new BigDecimal("2000")),
                        tuple(1002L, new BigDecimal("40000000"), new BigDecimal("2500")),
                        // 개별주: evaluationAmount=null (현재가×수량으로 산출, DB 미저장)
                        tuple(2001L, null, new BigDecimal("180")),
                        tuple(2002L, null, new BigDecimal("40")),
                        tuple(2003L, null, new BigDecimal("25"))
                );
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reusesExistingHoldingOnResyncSoHoldingIdIsStable() {
        // #207: 재동기화 시 같은 (account, productId) 보유행은 재사용해 holdingId를 유지해야 한다.
        // (deleteAll+insert로 holdingId가 재발급되면 월급 자산 제외(HOLDING_*)가 풀린다)
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        Holding existing = new Holding(mock(Account.class), 1001L,
                new BigDecimal("1"), new BigDecimal("1"));
        ReflectionTestUtils.setField(existing, "holdingId", 999L);
        when(holdingRepository.findByAccountIn(any())).thenReturn(List.of(existing));

        assetMockService.create(1L, MockType.NEED_IMPROVEMENT);

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(holdingRepository).saveAll(captor.capture());
        List<Holding> saved = toList((Iterable<Holding>) captor.getValue());

        Holding reused = saved.stream().filter(h -> h.getProductId().equals(1001L)).findFirst().orElseThrow();
        // 기존 행 재사용 → holdingId 유지, 평가액·수량만 갱신
        assertThat(reused.getHoldingId()).isEqualTo(999L);
        assertThat(reused.getEvaluationAmount()).isEqualByComparingTo("40000000");
        // 다른 productId는 신규 추가
        assertThat(saved).extracting(Holding::getProductId).contains(1002L);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void preservesUserConnectedInstitutionsOnResync() {
        // #223: 재동기화는 mock 시드(신한 계열)만 추가/갱신하고, 사용자가 마이페이지에서 직접
        // 연결한 기관(KB)은 삭제하지 않아야 한다. (이전 category-키 upsert는 같은 BANK의
        // 수동 연결을 '미매칭/중복'으로 보고 삭제·덮어써 마이페이지에서 사라지게 했다)
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        LocalDateTime earlier = LocalDateTime.now().minusDays(1);
        AssetConnection seededBank = new AssetConnection(user, "신한은행", "BANK", "CONNECTED", earlier);
        AssetConnection manualBank = new AssetConnection(user, "KB국민은행", "BANK", "CONNECTED", earlier);
        when(assetConnectionRepository.findByUserUserId(1L))
                .thenReturn(List.of(seededBank, manualBank));

        MockAssetResponse response = assetMockService.create(1L, MockType.NEED_IMPROVEMENT);

        // 수동 연결은 절대 삭제되지 않는다(연결 테이블엔 mock·수동 행이 섞여 있으므로).
        verify(assetConnectionRepository, never()).deleteAll(any());

        // 저장 대상은 신한 시드뿐 — KB는 건드리지 않는다.
        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(assetConnectionRepository).saveAll(captor.capture());
        List<AssetConnection> saved = toList((Iterable<AssetConnection>) captor.getValue());
        assertThat(saved).extracting(AssetConnection::getInstitutionName)
                .doesNotContain("KB국민은행");

        // 온보딩 카운트 == 마이페이지 카운트(#204): 시드 5곳 + 수동 KB = 6곳
        // (신한은행 BANK/LOAN은 기관 단위 1곳으로 합산).
        assertThat(response.connectedInstitutionCount()).isEqualTo(6);

        // generatedCounts.connections는 '생성/갱신한 시드 행 수'(6)만 의미한다.
        // 전체 연결 행(시드 6 + 수동 KB = 7)이 새어 들어가지 않는다.
        assertThat(response.generatedCounts().connections()).isEqualTo(6);
    }

    @Test
    void updatesExistingMockAssetsWhenSameUserChangesScenario() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Account checking = new Account(
                user, "CMA", "신한은행", "MOCK-1-1",
                BigDecimal.valueOf(6_000_000), true
        );
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of(checking));
        returnArgumentsFromSaveAll();

        MockAssetResponse response = assetMockService.create(1L, MockType.STABLE);

        assertThat(checking.getDepositBalance()).isEqualByComparingTo("35000000");
        // 개별주 evaluationAmount=null → createAssetSummary 합산에서 제외. STABLE: 계좌 305M + ETF 130M = 435M
        assertThat(response.assetSummary().totalAsset()).isEqualByComparingTo("435000000");
    }

    @Test
    void syncResolvesScenarioFromUserIdWithoutCaller() {
        User user = mock(User.class);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        MockAssetResponse response = assetMockService.sync(3L);

        // userId 3 → STABLE. 개별주 evaluationAmount=null이므로 총자산 = 계좌 305M + ETF 130M = 435M
        assertThat(response.assetSummary().totalAsset()).isEqualByComparingTo("435000000");
        assertThat(response.assetSummary().totalDebt()).isEqualByComparingTo("0");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void assignsAccountNumberByTypeSoDriftedLegacyDataDoesNotCollide() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // 드리프트된 레거시: BROKERAGE 계정이 인덱스1 번호(MOCK-1-1)를 점유.
        // (과거 인덱스 기반 번호 부여 + 시나리오 자산 순서 변경의 잔존 상태)
        Account drifted = new Account(
                user, "BROKERAGE", "신한투자증권", "MOCK-1-1", BigDecimal.valueOf(80_000_000), true);
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of(drifted));
        returnArgumentsFromSaveAll();

        assetMockService.create(1L, MockType.NEED_IMPROVEMENT);

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(accountRepository).saveAll(captor.capture());
        List<Account> saved = toList((Iterable<Account>) captor.getValue());

        // 모든 account_number가 accountType 기준으로 부여되고 서로 충돌하지 않는다.
        assertThat(saved).allSatisfy(account ->
                assertThat(account.getAccountNumber())
                        .isEqualTo("MOCK-1-" + account.getAccountType()));
        assertThat(saved).extracting(Account::getAccountNumber).doesNotHaveDuplicates();
        // 드리프트된 BROKERAGE는 매칭되어 번호가 재정렬되고 MOCK-1-1을 더는 보유하지 않는다.
        assertThat(saved).extracting(Account::getAccountNumber).doesNotContain("MOCK-1-1");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void currentMonthRegularEventsRecurringButConsumptionAndPastAreNot() {
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.create(1L, MockType.NEED_IMPROVEMENT);

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(cashFlowEventRepository).saveAll(captor.capture());
        List<CashFlowEvent> saved = toList((Iterable<CashFlowEvent>) captor.getValue());

        YearMonth currentMonth = YearMonth.now();
        List<CashFlowEvent> thisMonth = saved.stream()
                .filter(e -> YearMonth.from(e.getEventDate()).equals(currentMonth))
                .toList();
        List<CashFlowEvent> pastMonths = saved.stream()
                .filter(e -> YearMonth.from(e.getEventDate()).isBefore(currentMonth))
                .toList();

        // 현재월: 정기 수입·고정비는 recurring=true, 일회성 소비는 recurring=false (#177 — 캘린더 미래 투영 방지)
        Set<String> recurringTypes = Set.of("PENSION", "INTEREST", "MAINTENANCE", "INSURANCE", "LOAN");
        assertThat(thisMonth).isNotEmpty();
        assertThat(thisMonth).filteredOn(e -> recurringTypes.contains(e.getEventType()))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getRecurring()).isTrue());
        assertThat(thisMonth).filteredOn(e -> !recurringTypes.contains(e.getEventType()))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getRecurring()).isFalse());
        assertThat(pastMonths).isNotEmpty();
        assertThat(pastMonths).allSatisfy(e -> assertThat(e.getRecurring()).isFalse());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pensionCashflowEvent_notCreated_whenNationalPensionNotReceiving() {
        User user = mock(User.class);
        when(user.getNationalPensionReceiving()).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.create(1L, MockType.NEED_COMPLEMENT);

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(cashFlowEventRepository).saveAll(captor.capture());
        List<CashFlowEvent> saved = toList((Iterable<CashFlowEvent>) captor.getValue());

        assertThat(saved).noneMatch(e -> "PENSION".equals(e.getEventType()));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pensionCashflowEvent_created_whenNationalPensionReceiving() {
        User user = mock(User.class);
        when(user.getNationalPensionReceiving()).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.create(1L, MockType.NEED_COMPLEMENT);

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(cashFlowEventRepository).saveAll(captor.capture());
        List<CashFlowEvent> saved = toList((Iterable<CashFlowEvent>) captor.getValue());

        List<CashFlowEvent> pensionEvents = saved.stream()
                .filter(e -> "PENSION".equals(e.getEventType()))
                .toList();
        assertThat(pensionEvents).hasSize(12);
        assertThat(pensionEvents).allSatisfy(e -> {
            assertThat(e.getTitle()).isEqualTo("국민연금 입금");
            assertThat(e.getAmount()).isEqualByComparingTo("1150000");
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void monthlyReportSnapshotsReconcileWithNetCashFlow() {
        // 정합성 핵심(#256): 월별 총자산 스냅샷의 달간 증감 == 그 달 실제 변화량
        // = 순현금흐름(연금+이자 − 소비·관리비·보험·대출) + 배당(mock 0) + 시세변동(보유 평가액 × 월수익률).
        // 주식 매수/매도는 순자산 중립이라 제외. 시세변동이 더해져 자산이 늘고 줄 수 있다.
        User user = mock(User.class);
        when(user.getNationalPensionReceiving()).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        MockAssetResponse response = assetMockService.create(1L, MockType.STABLE);

        // 저장된 현금흐름에서 월별 순현금흐름(주식 제외) 재계산
        ArgumentCaptor<Iterable> cfCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(cashFlowEventRepository).saveAll(cfCaptor.capture());
        List<CashFlowEvent> events = toList((Iterable<CashFlowEvent>) cfCaptor.getValue());
        Map<YearMonth, BigDecimal> netByMonth = new HashMap<>();
        for (CashFlowEvent e : events) {
            if (e.getEventDate() == null
                    || "STOCK_BUY".equals(e.getEventType())
                    || "STOCK_SELL".equals(e.getEventType())) {
                continue;
            }
            YearMonth m = YearMonth.from(e.getEventDate());
            BigDecimal signed = "INCOME".equals(e.getFlowType()) ? e.getAmount() : e.getAmount().negate();
            netByMonth.merge(m, signed, BigDecimal::add);
        }

        // 시세변동 베이스 = 저장된 보유 평가액 합계 (개별주는 evaluationAmount=null이라 제외)
        ArgumentCaptor<Iterable> hCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(holdingRepository).saveAll(hCaptor.capture());
        BigDecimal holdingsBase = toList((Iterable<Holding>) hCaptor.getValue()).stream()
                .map(Holding::getEvaluationAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 저장된 12개월 스냅샷 수집
        ArgumentCaptor<MonthlyReport> snapCaptor = ArgumentCaptor.forClass(MonthlyReport.class);
        verify(monthlyReportRepository, atLeastOnce()).save(snapCaptor.capture());
        Map<String, BigDecimal> snapByMonth = snapCaptor.getAllValues().stream()
                .collect(Collectors.toMap(MonthlyReport::getCurrentMonth, MonthlyReport::getTotalAsset));

        YearMonth current = YearMonth.now();
        BigDecimal currentTotal = response.assetSummary().totalAsset();

        // 현재월: 현재 총자산 − 전월 스냅샷 == 이번달 변화량(순현금흐름 + 시세변동; 배당 mock 0)
        BigDecimal prevSnap = snapByMonth.get(current.minusMonths(1).toString());
        assertThat(prevSnap).isNotNull();
        assertThat(currentTotal.subtract(prevSnap))
                .isEqualByComparingTo(expectedDelta(netByMonth, holdingsBase, current));

        // 과거 연속 달도 동일하게 정합
        for (int i = 1; i <= 11; i++) {
            BigDecimal newer = snapByMonth.get(current.minusMonths(i).toString());
            BigDecimal older = snapByMonth.get(current.minusMonths(i + 1).toString());
            assertThat(newer.subtract(older))
                    .isEqualByComparingTo(expectedDelta(netByMonth, holdingsBase, current.minusMonths(i)));
        }
    }

    /** 그 달 자산 변화량 = 순현금흐름 + 배당(mock 0) + 시세변동(보유 평가액 × 월수익률). */
    private static BigDecimal expectedDelta(Map<YearMonth, BigDecimal> netByMonth,
                                            BigDecimal holdingsBase, YearMonth month) {
        BigDecimal market = holdingsBase.multiply(MonthlyVariation.marketReturn(month))
                .setScale(0, RoundingMode.HALF_UP);
        return netByMonth.getOrDefault(month, BigDecimal.ZERO).add(market);
    }

    @Test
    void seedWithExplicitPropensityOverridesScenarioDefault() {
        // 옵션2: 투자성향은 자산 시나리오와 독립 축. STABLE 자산(기본 성향 STABLE)에 공격투자형을 강제 지정하면
        // 시나리오 기본 성향 대신 override가 시드된다(같은 자산에 성향만 바꿔 운용등급 차등 시연).
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.seed(1L, MockType.STABLE, InvestmentPropensity.AGGRESSIVE);

        verify(user).assignInvestmentPropensity(InvestmentPropensity.AGGRESSIVE);
    }

    @Test
    void seedWithoutPropensityUsesScenarioDefault() {
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.seed(1L, MockType.STABLE, null);

        // override가 없으면 STABLE 시나리오 기본 성향(STABLE)을 그대로 쓴다.
        verify(user).assignInvestmentPropensity(InvestmentPropensity.STABLE);
    }

    @Test
    void recalculatesLifeStabilityAfterSync() {
        User user = mock(User.class);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.sync(3L);

        verify(lifeStabilityService).recalculateFromUserDataIfReady(3L);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void irpAccount_hasCompositionAndOpenedAt_afterCreate() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.create(1L, MockType.NEED_COMPLEMENT);

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(accountRepository).saveAll(captor.capture());
        List<Account> saved = toList((Iterable<Account>) captor.getValue());

        Account irp = saved.stream()
                .filter(a -> "IRP".equals(a.getAccountType()))
                .findFirst().orElseThrow();

        assertThat(irp.getIrpRetirementAmount()).isEqualByComparingTo("39000000");
        assertThat(irp.getIrpPersonalAmount()).isEqualByComparingTo("26000000");
        assertThat(irp.getOpenedAt()).isEqualTo(LocalDate.now().minusYears(15));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pensionSaving_hasOpenedAt_afterCreate() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.create(1L, MockType.NEED_COMPLEMENT);

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(accountRepository).saveAll(captor.capture());
        List<Account> saved = toList((Iterable<Account>) captor.getValue());

        Account ps = saved.stream()
                .filter(a -> "PENSION_SAVING".equals(a.getAccountType()))
                .findFirst().orElseThrow();

        assertThat(ps.getOpenedAt()).isEqualTo(LocalDate.now().minusYears(15));
    }

    private void returnArgumentsFromSaveAll() {
        lenient().when(etfPoolProvider.getPool()).thenReturn(etfPool());
        lenient().when(holdingRepository.findStockProductIds(any())).thenReturn(stockPool());
        // 운영은 IDENTITY로 accountId가 채워진다. saveHoldings가 brokerage.getAccountId()를 쓰므로
        // mock에서도 저장 시 ID를 부여해야 List.of(null) NPE가 나지 않는다(테스트 인프라 보정).
        lenient().when(accountRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Account> accounts = toList(invocation.getArgument(0));
            long nextId = 1L;
            for (Account account : accounts) {
                if (account.getAccountId() == null) {
                    ReflectionTestUtils.setField(account, "accountId", nextId++);
                }
            }
            return accounts;
        });
        lenient().when(holdingRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(pensionRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(debtRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(insurancePolicyRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(assetConnectionRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(cashFlowEventRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        // 예금 이자 계산에 필요한 productId 연결 및 금리 조회 stub
        lenient().when(depositDetailClient.fetchFirstDepositProductId()).thenReturn(Optional.of(9001L));
        lenient().when(depositDetailClient.fetchDepositDetails(any()))
                .thenReturn(Map.of(9001L, new DepositDetailItem(9001L, null, new BigDecimal("2.9"), 12)));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> toList(Iterable<T> values) {
        if (values instanceof List<?> list) {
            return (List<T>) list;
        }
        throw new IllegalArgumentException("Expected a list");
    }

    private static Stream<Arguments> scenarios() {
        // 개별주 evaluationAmount=null(현재가 기반 산출)이므로 createAssetSummary의 totalAsset에 포함되지 않는다.
        // totalAsset = 계좌 예수금 합 + ETF 평가액 합 (개별주 제외).
        //   NEED_IMPROVEMENT: 계좌 43M + ETF 80M = 123M (stock 26M 제외)
        //   NEED_COMPLEMENT:  계좌 153M + ETF 55M = 208M (stock 13M 제외)
        //   STABLE:           계좌 305M + ETF 130M = 435M (stock 5M 제외)
        // holdingCount = ETF + 개별주 보유행 수 (개별주도 DB에 저장되므로 총 카운트에 포함).
        // cashflowEvents = 12개월치(현재월 + 과거 11개월) buildMonthEvents 합산 (#256: 6→12개월 확장).
        // PENSION events (1/month × 12 months = 12) only appear when the flag is TRUE.
        return Stream.of(
                Arguments.of(MockType.NEED_IMPROVEMENT, 123_000_000L, 75_000_000L, 48_000_000L, 5,
                        InvestmentPropensity.ACTIVE, 444),
                Arguments.of(MockType.NEED_COMPLEMENT, 208_000_000L, 30_000_000L, 178_000_000L, 4,
                        InvestmentPropensity.NEUTRAL, 360),
                Arguments.of(MockType.STABLE, 435_000_000L, 0L, 435_000_000L, 4,
                        InvestmentPropensity.STABLE, 324)
        );
    }

    private static List<EtfInfo> etfPool() {
        return List.of(
                etf(1001L, "433330"),
                etf(1002L, "476030"),
                etf(1003L, "292500"),
                etf(1004L, "446720"),
                etf(1005L, "438560")
        );
    }

    private static List<StockTickerProductId> stockPool() {
        return List.of(
                stockRow("005930", 2001L),  // 삼성전자
                stockRow("000660", 2002L),  // SK하이닉스
                stockRow("005380", 2003L),  // 현대차
                stockRow("373220", 2004L)   // LG에너지솔루션
        );
    }

    private static StockTickerProductId stockRow(String ticker, Long productId) {
        return new StockTickerProductId() {
            @Override
            public String getTicker() {
                return ticker;
            }

            @Override
            public Long getProductId() {
                return productId;
            }
        };
    }

    private static EtfInfo etf(Long productId, String ticker) {
        return new EtfInfo(
                productId,
                ticker,
                ticker,
                1,
                BigDecimal.ZERO,
                null,
                null,
                null
        );
    }
}
