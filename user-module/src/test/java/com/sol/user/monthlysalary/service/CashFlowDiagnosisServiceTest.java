package com.sol.user.monthlysalary.service;

import com.sol.user.holding.dto.EtfHolding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.mapper.SalaryAssetMapper;
import com.sol.user.monthlysalary.repository.SalaryAssetExclusionRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CashFlowDiagnosisServiceTest {

    private final PensionRepository pensionRepository = mock(PensionRepository.class);
    private final UserGoalRepository userGoalRepository = mock(UserGoalRepository.class);
    private final HoldingRepository holdingRepository = mock(HoldingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProductBatchClient productBatchClient = mock(ProductBatchClient.class);
    private final SalaryAssetExclusionRepository exclusionRepository = mock(SalaryAssetExclusionRepository.class);
    private final SalaryAssetMapper salaryAssetMapper = new SalaryAssetMapper();

    private final CashFlowDiagnosisService service = new CashFlowDiagnosisService(
            pensionRepository, userGoalRepository, holdingRepository, userRepository,
            productBatchClient, exclusionRepository, salaryAssetMapper);

    @Test
    void 분배금은_15_4퍼센트_원천징수후_실수령으로_국민연금은_면세로_집계된다() {
        given(userRepository.existsById(1L)).willReturn(true);
        given(pensionRepository.findMonthlyAmount(1L, "NATIONAL"))
                .willReturn(Optional.of(BigDecimal.valueOf(1_000_000)));

        EtfHolding holding = mock(EtfHolding.class);
        given(holding.getHoldingId()).willReturn(1L);
        given(holding.getProductId()).willReturn(100L);
        given(holding.getQuantity()).willReturn(BigDecimal.valueOf(100));
        given(holdingRepository.findAllHoldingsByUserId(1L)).willReturn(List.of(holding));
        given(productBatchClient.fetchEtfMonthlyDividends(List.of(100L)))
                .willReturn(Map.of(100L, BigDecimal.valueOf(1_000)));  // 종목당 월분배 1,000원

        UserGoal goal = mock(UserGoal.class);
        given(goal.getMonthlyTargetLivingCost()).willReturn(BigDecimal.valueOf(2_000_000));
        given(userGoalRepository.findByUserUserId(1L)).willReturn(Optional.of(goal));

        CashFlowDiagnosisResponse res = service.diagnose(1L);

        // 분배금 gross = 1,000 × 100 = 100,000 → net = × (1−0.154) = 84,600
        assertThat(res.getDividendIncome()).isEqualByComparingTo("84600");
        // 국민연금 면세(0%) → 1,000,000 그대로
        assertThat(res.getNationalPension()).isEqualByComparingTo("1000000");
        // 합 = 1,084,600
        assertThat(res.getMonthlyCashFlow()).isEqualByComparingTo("1084600");
    }
}
