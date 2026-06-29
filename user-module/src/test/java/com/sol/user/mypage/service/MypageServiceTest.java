package com.sol.user.mypage.service;

import com.sol.user.mypage.dto.UserProfileUpdateRequest;
import com.sol.user.monthlysalary.entity.SalaryPlan;
import com.sol.user.monthlysalary.repository.SalaryPlanRepository;
import com.sol.user.stability.service.LifeStabilityService;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MypageServiceTest {

    private static final Long USER_ID = 1L;

    @Mock UserRepository userRepository;
    @Mock UserGoalRepository userGoalRepository;
    @Mock SalaryPlanRepository salaryPlanRepository;
    @Mock LifeStabilityService lifeStabilityService;

    @InjectMocks MypageService mypageService;

    @Test
    void updateProfileSupersedesActiveSalaryPlanAndRecalculatesWhenTargetLivingCostChanges() {
        User user = mock(User.class);
        UserGoal goal = new UserGoal(user, money(3_000_000), money(350_000), LocalDateTime.now());
        SalaryPlan activePlan = mock(SalaryPlan.class);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.of(goal));
        when(salaryPlanRepository.findByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE))
                .thenReturn(Optional.of(activePlan));

        mypageService.updateProfile(USER_ID, request(money(3_500_000)));

        assertThat(goal.getMonthlyTargetLivingCost()).isEqualByComparingTo("3500000");
        verify(activePlan).supersede();
        verify(lifeStabilityService).recalculateFromUserDataIfReady(USER_ID);
    }

    @Test
    void updateProfileKeepsSalaryPlanWhenTargetLivingCostIsSame() {
        User user = mock(User.class);
        UserGoal goal = new UserGoal(user, money(3_000_000), money(350_000), LocalDateTime.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.of(goal));

        mypageService.updateProfile(USER_ID, request(money(3_000_000)));

        verifyNoInteractions(salaryPlanRepository);
        verify(lifeStabilityService, never()).recalculateFromUserDataIfReady(USER_ID);
    }

    private UserProfileUpdateRequest request(BigDecimal monthlyTargetKrw) {
        return new UserProfileUpdateRequest(null, null, null, monthlyTargetKrw);
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount);
    }
}
