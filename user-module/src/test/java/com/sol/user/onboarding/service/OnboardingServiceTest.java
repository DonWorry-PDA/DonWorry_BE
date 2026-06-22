package com.sol.user.onboarding.service;

import com.sol.user.onboarding.dto.OnboardingRequest;
import com.sol.user.onboarding.dto.OnboardingResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserGoalRepository userGoalRepository;

    @InjectMocks OnboardingService onboardingService;

    @Test
    void appliesDemoDefaultsWhenGoalValuesOmitted() {
        User user = new User();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(userGoalRepository.save(any(UserGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingResponse response = onboardingService.complete(
                1L, new OnboardingRequest(60, true, false, null, null));

        assertThat(response.monthlyTargetLivingCost()).isEqualByComparingTo("2200000");
        assertThat(response.monthlyExpectedMedicalCost()).isEqualByComparingTo("350000");
        assertThat(response.age()).isEqualTo(60);
        assertThat(response.onboardingCompleted()).isTrue();
    }

    @Test
    void updatesExistingGoalWhenPresent() {
        User user = new User();
        UserGoal existing = new UserGoal(user, BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(100_000), LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userGoalRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.of(existing));
        when(userGoalRepository.save(any(UserGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingResponse response = onboardingService.complete(
                1L, new OnboardingRequest(null, null, null,
                        BigDecimal.valueOf(2_200_000), BigDecimal.valueOf(350_000)));

        assertThat(existing.getMonthlyTargetLivingCost()).isEqualByComparingTo("2200000");
        assertThat(existing.getMonthlyExpectedMedicalCost()).isEqualByComparingTo("350000");
        assertThat(response.monthlyTargetLivingCost()).isEqualByComparingTo("2200000");
    }
}
