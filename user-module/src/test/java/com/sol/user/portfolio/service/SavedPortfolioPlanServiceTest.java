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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedPortfolioPlanServiceTest {

    private static final Long USER_ID = 1L;

    @Mock SavedPortfolioPlanRepository savedPlanRepository;
    @Mock UserRepository userRepository;
    @Mock SavedPortfolioPlanMapper savedPortfolioPlanMapper;

    @InjectMocks SavedPortfolioPlanService service;

    @Test
    void 최초_저장_신규_엔티티가_save된다() {
        when(savedPlanRepository.findByUserUserId(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        when(savedPortfolioPlanMapper.toItem(any())).thenReturn(mock(SavedPortfolioPlanItem.class));
        when(savedPortfolioPlanMapper.toSaveResponse(any())).thenReturn(new SavePlanResponse(LocalDateTime.now()));

        service.save(USER_ID, request(PlanType.STABLE));

        ArgumentCaptor<SavedPortfolioPlan> captor = ArgumentCaptor.forClass(SavedPortfolioPlan.class);
        verify(savedPlanRepository).save(captor.capture());
        assertThat(captor.getValue().getPlanType()).isEqualTo(PlanType.STABLE);
        assertThat(captor.getValue().getMonthlyIncome()).isEqualByComparingTo("1680000");
    }

    @Test
    void 기존_저장_있으면_update_호출하고_save_호출_안함() {
        SavedPortfolioPlan existing = mock(SavedPortfolioPlan.class);
        when(savedPlanRepository.findByUserUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(savedPortfolioPlanMapper.toItem(any())).thenReturn(mock(SavedPortfolioPlanItem.class));
        when(savedPortfolioPlanMapper.toSaveResponse(any())).thenReturn(new SavePlanResponse(LocalDateTime.now()));

        service.save(USER_ID, request(PlanType.BALANCED));

        verify(existing).update(eq(PlanType.BALANCED), any(), any(), any(), any(), any(), any(), any());
        verify(savedPlanRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void 최초_저장_유저_없으면_USER_NOT_FOUND() {
        when(savedPlanRepository.findByUserUserId(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(USER_ID, request(PlanType.STABLE)))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 저장된_설계안_있으면_응답_반환() {
        SavedPortfolioPlan plan = mock(SavedPortfolioPlan.class);
        when(savedPlanRepository.findByUserUserId(USER_ID)).thenReturn(Optional.of(plan));
        SavedPlanResponse expected = savedPlanResponse(PlanType.STABLE);
        when(savedPortfolioPlanMapper.toSavedPlanResponse(plan)).thenReturn(expected);

        SavedPlanResponse result = service.getSaved(USER_ID);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void 저장된_설계안_없으면_null_반환() {
        when(savedPlanRepository.findByUserUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getSaved(USER_ID)).isNull();
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
        return new SavedPlanResponse(planType,
                new BigDecimal("1680000"),
                new BigDecimal("59.00"), new BigDecimal("84.00"),
                new BigDecimal("900000"), BigDecimal.ZERO,
                new BigDecimal("200000000"),
                List.of(),
                LocalDateTime.now());
    }
}
