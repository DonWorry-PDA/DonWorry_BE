package com.sol.user.monthlysalary.service;

import com.sol.user.cashflow.service.MonthlyCashFlowProjection;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection.ProjectedMonth;
import com.sol.user.holding.service.EtfDividendCalculator;
import com.sol.user.holding.service.EtfDividendCalculator.DividendBreakdown;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.mapper.SalaryAssetMapper;
import com.sol.user.monthlysalary.repository.SalaryAssetExclusionRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
    private final MonthlyCashFlowProjection projection = mock(MonthlyCashFlowProjection.class);

    private final CashFlowDiagnosisService service = new CashFlowDiagnosisService(
            pensionRepository, userGoalRepository, userRepository,
            exclusionRepository, salaryAssetMapper, etfDividendCalculator, projection);

    private void stubNoInterest() {
        given(projection.project(eq(1L), any())).willReturn(new ProjectedMonth(List.of()));
    }

    @Test
    void 분배금은_15_4퍼센트_원천징수후_실수령으로_국민연금은_면세로_집계된다() {
        User user = receivingUser(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(pensionRepository.findMonthlyAmount(1L, "NATIONAL"))
                .willReturn(Optional.of(BigDecimal.valueOf(1_000_000)));

        // 단일 출처(EtfDividendCalculator)가 세전 분배금 100,000을 돌려준다(100주 × 1,000).
        given(etfDividendCalculator.monthlyDividendBreakdown(eq(1L), any()))
                .willReturn(new DividendBreakdown(BigDecimal.valueOf(100_000), BigDecimal.ZERO));
        stubNoInterest();

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

    @Test
    void 국민연금_미수령이면_예상연금이_있어도_계상하지_않는다() {
        User user = receivingUser(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        // 미수령이라도 예상연금 값은 존재할 수 있으나, 현재 수령 중이 아니므로 계상하면 안 된다.
        given(pensionRepository.findMonthlyAmount(1L, "NATIONAL"))
                .willReturn(Optional.of(BigDecimal.valueOf(1_200_000)));
        given(etfDividendCalculator.monthlyDividendBreakdown(eq(1L), any()))
                .willReturn(DividendBreakdown.ZERO);
        stubNoInterest();

        UserGoal goal = mock(UserGoal.class);
        given(goal.getMonthlyTargetLivingCost()).willReturn(BigDecimal.valueOf(2_000_000));
        given(userGoalRepository.findByUserUserId(1L)).willReturn(Optional.of(goal));

        CashFlowDiagnosisResponse res = service.diagnose(1L);

        assertThat(res.getNationalPension()).isEqualByComparingTo("0");
        assertThat(res.getMonthlyCashFlow()).isEqualByComparingTo("0");
    }

    @Test
    void 예금이자도_원천징수후_실수령으로_현금흐름에_포함된다() {
        User user = receivingUser(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(pensionRepository.findMonthlyAmount(1L, "NATIONAL"))
                .willReturn(Optional.of(BigDecimal.valueOf(900_000)));
        given(etfDividendCalculator.monthlyDividendBreakdown(eq(1L), any()))
                .willReturn(DividendBreakdown.ZERO);
        given(projection.project(eq(1L), any())).willReturn(new ProjectedMonth(List.of(
                new MonthlyCashFlowProjection.ProjectedEvent("INTEREST", "INCOME", BigDecimal.valueOf(50_000)))));

        UserGoal goal = mock(UserGoal.class);
        given(goal.getMonthlyTargetLivingCost()).willReturn(BigDecimal.valueOf(2_000_000));
        given(userGoalRepository.findByUserUserId(1L)).willReturn(Optional.of(goal));

        CashFlowDiagnosisResponse res = service.diagnose(1L);

        // 이자 gross 50,000 → net = × (1−0.154) = 42,300
        assertThat(res.getInterestIncome()).isEqualByComparingTo("42300");
        // 국민연금 900,000 + 이자 42,300
        assertThat(res.getMonthlyCashFlow()).isEqualByComparingTo("942300");
    }

    private User receivingUser(boolean receiving) {
        User user = mock(User.class);
        given(user.getNationalPensionReceiving()).willReturn(receiving);
        return user;
    }
}
