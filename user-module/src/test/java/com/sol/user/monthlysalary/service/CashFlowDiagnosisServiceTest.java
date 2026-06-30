package com.sol.user.monthlysalary.service;

import com.sol.user.holding.service.EtfDividendCalculator;
import com.sol.user.holding.service.EtfDividendCalculator.DividendBreakdown;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.mapper.SalaryAssetMapper;
import com.sol.user.monthlysalary.repository.SalaryAssetExclusionRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CashFlowDiagnosisServiceTest {

    private final PensionRepository pensionRepository = mock(PensionRepository.class);
    private final UserGoalRepository userGoalRepository = mock(UserGoalRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SalaryAssetExclusionRepository exclusionRepository = mock(SalaryAssetExclusionRepository.class);
    private final SalaryAssetMapper salaryAssetMapper = new SalaryAssetMapper();
    private final EtfDividendCalculator etfDividendCalculator = mock(EtfDividendCalculator.class);

    private final CashFlowDiagnosisService service = new CashFlowDiagnosisService(
            pensionRepository, userGoalRepository, userRepository,
            exclusionRepository, salaryAssetMapper, etfDividendCalculator);

    @Test
    void 분배금은_15_4퍼센트_원천징수후_실수령으로_국민연금은_면세로_집계된다() {
        given(userRepository.existsById(1L)).willReturn(true);
        given(pensionRepository.findMonthlyAmount(1L, "NATIONAL"))
                .willReturn(Optional.of(BigDecimal.valueOf(1_000_000)));

        // 단일 출처(EtfDividendCalculator)가 세전 분배금 100,000을 돌려준다(100주 × 1,000).
        given(etfDividendCalculator.monthlyDividendBreakdown(eq(1L), any()))
                .willReturn(new DividendBreakdown(BigDecimal.valueOf(100_000), BigDecimal.ZERO));

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
