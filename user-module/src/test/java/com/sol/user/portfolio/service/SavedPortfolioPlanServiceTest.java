package com.sol.user.portfolio.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.portfolio.dto.SavePlanRequest;
import com.sol.user.portfolio.dto.SavePlanResponse;
import com.sol.user.portfolio.dto.SavedPlanResponse;
import com.sol.user.portfolio.entity.SavedPortfolioPlan;
import com.sol.user.portfolio.entity.SavedPortfolioPlanItem;
import com.sol.user.portfolio.mapper.SavedPortfolioPlanMapper;
import com.sol.user.portfolio.repository.SavedPortfolioPlanRepository;
import com.sol.user.portfolio.type.PlanType;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedPortfolioPlanServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    @Mock SavedPortfolioPlanRepository savedPlanRepository;
    @Mock UserRepository userRepository;
    @Mock SavedPortfolioPlanMapper savedPortfolioPlanMapper;

    @InjectMocks SavedPortfolioPlanService service;

    @Test
    void 저장_시_항상_새_엔티티가_save된다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        when(savedPortfolioPlanMapper.toItem(any())).thenReturn(mock(SavedPortfolioPlanItem.class));
        when(savedPortfolioPlanMapper.toSaveResponse(any(), any())).thenReturn(new SavePlanResponse(null, LocalDateTime.now()));

        service.save(USER_ID, request(PlanType.STABLE));

        ArgumentCaptor<SavedPortfolioPlan> captor = ArgumentCaptor.forClass(SavedPortfolioPlan.class);
        verify(savedPlanRepository).save(captor.capture());
        assertThat(captor.getValue().getPlanType()).isEqualTo(PlanType.STABLE);
        assertThat(captor.getValue().getMonthlyIncome()).isEqualByComparingTo("1680000");
    }

    @Test
    void 저장_유저_없으면_USER_NOT_FOUND() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(USER_ID, request(PlanType.STABLE)))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 저장된_설계안_목록_반환() {
        SavedPortfolioPlan plan1 = mock(SavedPortfolioPlan.class);
        SavedPortfolioPlan plan2 = mock(SavedPortfolioPlan.class);
        when(savedPlanRepository.findByUserUserId(USER_ID)).thenReturn(List.of(plan1, plan2));
        SavedPlanResponse r1 = savedPlanResponse(PlanType.STABLE);
        SavedPlanResponse r2 = savedPlanResponse(PlanType.BALANCED);
        when(savedPortfolioPlanMapper.toSavedPlanResponse(plan1)).thenReturn(r1);
        when(savedPortfolioPlanMapper.toSavedPlanResponse(plan2)).thenReturn(r2);

        List<SavedPlanResponse> result = service.getSavedList(USER_ID);

        assertThat(result).containsExactly(r1, r2);
    }

    @Test
    void 저장된_설계안_없으면_빈_목록_반환() {
        when(savedPlanRepository.findByUserUserId(USER_ID)).thenReturn(List.of());

        assertThat(service.getSavedList(USER_ID)).isEmpty();
    }

    @Test
    void planId와_userId_일치하면_삭제() {
        SavedPortfolioPlan plan = mock(SavedPortfolioPlan.class);
        when(savedPlanRepository.findByIdAndUserUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));

        service.delete(USER_ID, PLAN_ID);

        verify(savedPlanRepository).delete(plan);
    }

    @Test
    void planId가_없거나_다른_유저_소유면_SAVED_PLAN_NOT_FOUND() {
        when(savedPlanRepository.findByIdAndUserUserId(PLAN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, PLAN_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.SAVED_PLAN_NOT_FOUND);
    }

    private SavePlanRequest request(PlanType planType) {
        return new SavePlanRequest(
                planType,
                new BigDecimal("1680000"),
                new BigDecimal("59.00"), new BigDecimal("84.00"),
                new BigDecimal("900000"), BigDecimal.ZERO,
                new BigDecimal("200000000"),
                List.of(new SavePlanRequest.HoldingItem(101L, "069500", "KODEX 200",
                        new BigDecimal("0.60"), new BigDecimal("120000000"))));
    }

    private SavedPlanResponse savedPlanResponse(PlanType planType) {
        return new SavedPlanResponse(1L, planType,
                new BigDecimal("1680000"),
                new BigDecimal("59.00"), new BigDecimal("84.00"),
                new BigDecimal("900000"), BigDecimal.ZERO,
                new BigDecimal("200000000"),
                List.of(),
                LocalDateTime.now());
    }
}
