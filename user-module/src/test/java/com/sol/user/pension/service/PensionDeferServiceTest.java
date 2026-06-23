package com.sol.user.pension.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.pension.calculator.PensionDeferCalculator;
import com.sol.user.pension.dto.PensionDeferComparisonRow;
import com.sol.user.pension.dto.PensionDeferResponse;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PensionDeferServiceTest {

    @Mock private PensionRepository pensionRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private UserGoalRepository userGoalRepository;
    @Mock private PensionDeferCalculator calculator;
    @Mock private UserGoal mockUserGoal;

    private PensionDeferService service;

    @BeforeEach
    void setUp() {
        service = new PensionDeferService(pensionRepository, holdingRepository,
            userGoalRepository, calculator);
    }

    @Test
    @DisplayName("유효하지 않은 deferRate(30) → INVALID_INPUT")
    void compare_invalidDeferRate_throwsInvalidInput() {
        assertThatThrownBy(() -> service.compare(1L, 30, 5))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("범위 밖 deferYears(6) → INVALID_INPUT")
    void compare_invalidDeferYears_throwsInvalidInput() {
        assertThatThrownBy(() -> service.compare(1L, 70, 6))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("국민연금 미등록 → PENSION_NOT_FOUND")
    void compare_noPension_throwsPensionNotFound() {
        when(pensionRepository.findMonthlyAmount(1L, "NATIONAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(1L, 70, 5))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(ErrorCode.PENSION_NOT_FOUND);
    }

    @Test
    @DisplayName("목표 생활비 미설정 → USER_GOAL_NOT_FOUND")
    void compare_noUserGoal_throwsUserGoalNotFound() {
        when(pensionRepository.findMonthlyAmount(1L, "NATIONAL"))
            .thenReturn(Optional.of(BigDecimal.valueOf(1_200_000)));
        when(holdingRepository.sumMonthlyDividendByUserId(1L))
            .thenReturn(BigDecimal.valueOf(100_000));
        when(userGoalRepository.findByUserUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(1L, 70, 5))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(ErrorCode.USER_GOAL_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 호출 → selected.deferRate=70, comparisonTable 포함")
    void compare_valid_returnsResponse() {
        BigDecimal base = BigDecimal.valueOf(1_200_000);
        BigDecimal dividend = BigDecimal.valueOf(100_000);
        BigDecimal target = BigDecimal.valueOf(2_200_000);

        when(pensionRepository.findMonthlyAmount(1L, "NATIONAL")).thenReturn(Optional.of(base));
        when(holdingRepository.sumMonthlyDividendByUserId(1L)).thenReturn(dividend);
        when(userGoalRepository.findByUserUserId(1L)).thenReturn(Optional.of(mockUserGoal));
        when(mockUserGoal.getMonthlyTargetLivingCost()).thenReturn(target);

        List<PensionDeferComparisonRow> mockRows = List.of(
            PensionDeferComparisonRow.builder().deferRate(0)
                .duringDeferMonthly(1_200_000).afterDeferMonthly(1_200_000).monthlyIncrease(0)
                .coverageRateDuring(59).coverageRateAfter(59)
                .stabilityDuring("주의").stabilityAfter("주의").build(),
            PensionDeferComparisonRow.builder().deferRate(70)
                .duringDeferMonthly(360_000).afterDeferMonthly(1_502_400).monthlyIncrease(302_400)
                .breakEvenMonths(167L).coverageRateDuring(21).coverageRateAfter(72)
                .stabilityDuring("주의").stabilityAfter("안정").build()
        );
        when(calculator.calcAllRows(base, 5, dividend, target)).thenReturn(mockRows);
        when(calculator.generateInsight(70, mockRows)).thenReturn("테스트 인사이트");

        PensionDeferResponse response = service.compare(1L, 70, 5);

        assertThat(response.selected().deferRate()).isEqualTo(70);
        assertThat(response.selected().coverageRateBefore()).isEqualTo(59);
        assertThat(response.selected().insight()).isEqualTo("테스트 인사이트");
        assertThat(response.comparisonTable()).isEqualTo(mockRows);
    }
}
