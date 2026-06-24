package com.sol.user.retirement.service;

import com.sol.common.exception.BaseException;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.pension.entity.Pension;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.retirement.dto.RetirementSimParamsResponse;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetirementSimParamsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private UserGoalRepository userGoalRepository;
    @Mock private PensionRepository pensionRepository;

    private RetirementSimParamsService service;

    @BeforeEach
    void setUp() {
        service = new RetirementSimParamsService(
                userRepository, accountRepository, userGoalRepository, pensionRepository);
    }

    @Test
    @DisplayName("정상 데이터가 있으면 4개 필드를 합산하여 반환한다")
    void getParams_allDataPresent_returnsAggregated() {
        User user = mock(User.class);
        when(user.getAge()).thenReturn(63);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Account acc1 = new Account(user, "BROKERAGE", "신한은행", "111-11",
                new BigDecimal("150000000"), true);
        Account acc2 = new Account(user, "DEPOSIT", "신한은행", "222-22",
                new BigDecimal("100000000"), true);
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of(acc1, acc2));

        UserGoal goal = new UserGoal(user, new BigDecimal("2200000"),
                new BigDecimal("500000"), LocalDateTime.now());
        when(userGoalRepository.findByUserUserId(1L)).thenReturn(Optional.of(goal));

        Pension pension = new Pension(user, "NATIONAL", new BigDecimal("1200000"), false, 65);
        when(pensionRepository.findByUserUserId(1L)).thenReturn(List.of(pension));

        RetirementSimParamsResponse result = service.getParams(1L);

        assertThat(result.ageYears()).isEqualTo(63);
        assertThat(result.totalAssetsKrw()).isEqualByComparingTo(new BigDecimal("250000000"));
        assertThat(result.monthlyLivingKrw()).isEqualByComparingTo(new BigDecimal("2200000"));
        assertThat(result.monthlyPensionKrw()).isEqualByComparingTo(new BigDecimal("1200000"));
    }

    @Test
    @DisplayName("UserGoal이 없으면 monthlyLivingKrw는 0이다")
    void getParams_noUserGoal_livingCostIsZero() {
        User user = mock(User.class);
        when(user.getAge()).thenReturn(63);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of());
        when(userGoalRepository.findByUserUserId(1L)).thenReturn(Optional.empty());
        when(pensionRepository.findByUserUserId(1L)).thenReturn(List.of());

        RetirementSimParamsResponse result = service.getParams(1L);

        assertThat(result.monthlyLivingKrw()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("연금이 여러 개이면 합산한다")
    void getParams_multiplePensions_sumsAll() {
        User user = mock(User.class);
        when(user.getAge()).thenReturn(63);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of());
        when(userGoalRepository.findByUserUserId(1L)).thenReturn(Optional.empty());

        Pension p1 = new Pension(user, "NATIONAL", new BigDecimal("800000"), false, 65);
        Pension p2 = new Pension(user, "SAVING", new BigDecimal("400000"), false, 65);
        when(pensionRepository.findByUserUserId(1L)).thenReturn(List.of(p1, p2));

        RetirementSimParamsResponse result = service.getParams(1L);

        assertThat(result.monthlyPensionKrw()).isEqualByComparingTo(new BigDecimal("1200000"));
    }

    @Test
    @DisplayName("UserGoal이 있어도 monthlyTargetLivingCost가 null이면 0을 반환한다")
    void getParams_userGoalPresentButLivingCostNull_returnsZero() {
        User user = mock(User.class);
        when(user.getAge()).thenReturn(50);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountRepository.findByUserUserId(1L)).thenReturn(List.of());
        UserGoal goalWithNullCost = new UserGoal(user, null, null, LocalDateTime.now());
        when(userGoalRepository.findByUserUserId(1L)).thenReturn(Optional.of(goalWithNullCost));
        when(pensionRepository.findByUserUserId(1L)).thenReturn(List.of());

        RetirementSimParamsResponse result = service.getParams(1L);

        assertThat(result.monthlyLivingKrw()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("존재하지 않는 userId이면 BaseException을 던진다")
    void getParams_userNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getParams(99L))
                .isInstanceOf(BaseException.class);
    }
}
