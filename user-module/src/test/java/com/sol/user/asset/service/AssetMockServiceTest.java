package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.MockAssetResponse;
import com.sol.user.asset.type.MockType;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.dto.StockTickerProductId;
import com.sol.user.holding.entity.Holding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.portfolio.type.InvestmentPropensity;
import com.sol.user.stability.service.LifeStabilityService;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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

    @InjectMocks AssetMockService assetMockService;

    @ParameterizedTest
    @MethodSource("scenarios")
    void createsScenarioWithSpecifiedSummary(MockType mockType, long totalAsset,
                                             long totalDebt, long netAsset, int holdingCount,
                                             InvestmentPropensity expectedPropensity) {
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        MockAssetResponse response = assetMockService.create(1L, mockType);

        assertThat(response.assetSummary().totalAsset()).isEqualByComparingTo(BigDecimal.valueOf(totalAsset));
        assertThat(response.assetSummary().totalDebt()).isEqualByComparingTo(BigDecimal.valueOf(totalDebt));
        assertThat(response.assetSummary().netAsset()).isEqualByComparingTo(BigDecimal.valueOf(netAsset));
        assertThat(response.generatedCounts().connections()).isEqualTo(6);
        assertThat(response.generatedCounts().cashflowEvents()).isEqualTo(7);
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
                        // ETF(화이트리스트) 먼저
                        tuple(1001L, new BigDecimal("40000000"), new BigDecimal("2000")),
                        tuple(1002L, new BigDecimal("40000000"), new BigDecimal("2500")),
                        // 개별주
                        tuple(2001L, new BigDecimal("12000000"), new BigDecimal("180")),
                        tuple(2002L, new BigDecimal("8000000"), new BigDecimal("40")),
                        tuple(2003L, new BigDecimal("6000000"), new BigDecimal("25"))
                );
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
        assertThat(response.assetSummary().totalAsset()).isEqualByComparingTo("440000000");
    }

    @Test
    void syncResolvesScenarioFromUserIdWithoutCaller() {
        User user = mock(User.class);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        MockAssetResponse response = assetMockService.sync(3L);

        // userId 3 → STABLE (총자산 4억 4,000만 = 기존 4억 3,500만 + 개별주 500만, 무부채)
        assertThat(response.assetSummary().totalAsset()).isEqualByComparingTo("440000000");
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
    void recalculatesLifeStabilityAfterSync() {
        User user = mock(User.class);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        assetMockService.sync(3L);

        verify(lifeStabilityService).recalculateFromUserDataIfReady(3L);
    }

    private void returnArgumentsFromSaveAll() {
        lenient().when(etfPoolProvider.getPool()).thenReturn(etfPool());
        lenient().when(holdingRepository.findStockProductIds(any())).thenReturn(stockPool());
        lenient().when(accountRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(holdingRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(pensionRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(debtRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(insurancePolicyRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(assetConnectionRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
        lenient().when(cashFlowEventRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> toList(Iterable<T> values) {
        if (values instanceof List<?> list) {
            return (List<T>) list;
        }
        throw new IllegalArgumentException("Expected a list");
    }

    private static Stream<Arguments> scenarios() {
        // 개별주 시드 추가분이 순자산/보유종목수에 반영됨:
        //  NEED_IMPROVEMENT +26M(3종), NEED_COMPLEMENT +13M(2종), STABLE +5M(1종)
        return Stream.of(
                Arguments.of(MockType.NEED_IMPROVEMENT, 149_000_000L, 75_000_000L, 74_000_000L, 5,
                        InvestmentPropensity.ACTIVE),
                Arguments.of(MockType.NEED_COMPLEMENT, 221_000_000L, 30_000_000L, 191_000_000L, 4,
                        InvestmentPropensity.NEUTRAL),
                Arguments.of(MockType.STABLE, 440_000_000L, 0L, 440_000_000L, 4,
                        InvestmentPropensity.STABLE)
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
