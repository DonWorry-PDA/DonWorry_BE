package com.sol.user.asset.service;

import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.MockAssetResponse;
import com.sol.user.asset.type.MockType;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.insurance.repository.InsurancePolicyRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.stability.repository.StabilityScoreRepository;
import com.sol.user.stability.service.LifeStabilityService;
import com.sol.user.trade.repository.TradeHistoryRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetMockServiceTest {

    @Mock UserRepository userRepository;
    @Mock AccountRepository accountRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock TradeHistoryRepository tradeHistoryRepository;
    @Mock PensionRepository pensionRepository;
    @Mock UserGoalRepository userGoalRepository;
    @Mock CashFlowEventRepository cashFlowEventRepository;
    @Mock AssetConnectionRepository assetConnectionRepository;
    @Mock DebtRepository debtRepository;
    @Mock InsurancePolicyRepository insurancePolicyRepository;
    @Mock StabilityScoreRepository stabilityScoreRepository;
    @Mock LifeStabilityService lifeStabilityService;

    @InjectMocks AssetMockService assetMockService;

    @ParameterizedTest
    @MethodSource("scenarios")
    void createsScenarioWithSpecifiedSummary(MockType mockType, long totalAsset,
                                             long totalDebt, long securedCashflow,
                                             long monthlyGap, int coverageRate) {
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        returnArgumentsFromSaveAll();

        MockAssetResponse response = assetMockService.create(1L, mockType);

        assertThat(response.assetSummary().totalAsset()).isEqualByComparingTo(BigDecimal.valueOf(totalAsset));
        assertThat(response.assetSummary().totalDebt()).isEqualByComparingTo(BigDecimal.valueOf(totalDebt));
        assertThat(response.assetSummary().securedMonthlyCashflow()).isEqualByComparingTo(BigDecimal.valueOf(securedCashflow));
        assertThat(response.assetSummary().monthlyGap()).isEqualByComparingTo(BigDecimal.valueOf(monthlyGap));
        assertThat(response.assetSummary().cashflowCoverageRate()).isEqualByComparingTo(BigDecimal.valueOf(coverageRate));
        assertThat(response.generatedCounts().connections()).isEqualTo(6);
        assertThat(response.generatedCounts().cashflowEvents()).isEqualTo(7);

    }

    private void returnArgumentsFromSaveAll() {
        lenient().when(accountRepository.saveAll(any())).thenAnswer(invocation -> toList(invocation.getArgument(0)));
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
                Arguments.of(MockType.NEED_IMPROVEMENT, 123_000_000L, 75_000_000L, 704_000L, -1_496_000L, 32),
                Arguments.of(MockType.NEED_COMPLEMENT, 208_000_000L, 30_000_000L, 1_298_000L, -902_000L, 59),
                Arguments.of(MockType.STABLE, 435_000_000L, 0L, 2_464_000L, 264_000L, 112)
        );
    }
}
