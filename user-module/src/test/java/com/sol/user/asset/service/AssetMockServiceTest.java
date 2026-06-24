package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.MockAssetResponse;
import com.sol.user.asset.type.MockType;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.entity.Holding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.provider.EtfPoolProvider;
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
                                             long totalDebt, long netAsset, int holdingCount) {
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
                        tuple(1001L, new BigDecimal("40000000"), new BigDecimal("2000")),
                        tuple(1002L, new BigDecimal("40000000"), new BigDecimal("2500"))
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
        assertThat(response.assetSummary().totalAsset()).isEqualByComparingTo("435000000");
    }

    @Test
    void syncResolvesScenarioFromUserIdWithoutCaller() {
        User user = mock(User.class);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        MockAssetResponse response = assetMockService.sync(3L);

        // userId 3 → STABLE (총자산 4억 3,500만, 무부채)
        assertThat(response.assetSummary().totalAsset()).isEqualByComparingTo("435000000");
        assertThat(response.assetSummary().totalDebt()).isEqualByComparingTo("0");
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
        return Stream.of(
                Arguments.of(MockType.NEED_IMPROVEMENT, 123_000_000L, 75_000_000L, 48_000_000L, 2),
                Arguments.of(MockType.NEED_COMPLEMENT, 208_000_000L, 30_000_000L, 178_000_000L, 2),
                Arguments.of(MockType.STABLE, 435_000_000L, 0L, 435_000_000L, 3)
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
