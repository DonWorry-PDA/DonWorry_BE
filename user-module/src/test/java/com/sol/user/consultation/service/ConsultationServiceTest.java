package com.sol.user.consultation.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.consultation.dto.ConsultationCreateRequest;
import com.sol.user.consultation.dto.ConsultationMemoUpdateRequest;
import com.sol.user.consultation.dto.ConsultationResponse;
import com.sol.user.consultation.dto.ConsultationScheduleUpdateRequest;
import com.sol.user.consultation.entity.Consultation;
import com.sol.user.consultation.repository.ConsultationRepository;
import com.sol.user.consultation.repository.ConsultationSummaryRepository;
import com.sol.user.consultation.type.ConsultMethod;
import com.sol.user.consultation.type.ConsultStatus;
import com.sol.user.consultation.type.ConsultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultationServiceTest {

    private ConsultationRepository consultationRepository;
    private ConsultationSummaryRepository summaryRepository;
    private ConsultationService service;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationRepository.class);
        summaryRepository = mock(ConsultationSummaryRepository.class);
        service = new ConsultationService(consultationRepository, summaryRepository);
    }

    @Test
    void createAssignsReservedStatusAndTypeDefaults() {
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConsultationResponse response = service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), 7L));

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        Consultation saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(ConsultStatus.RESERVED);
        assertThat(saved.getMethod()).isEqualTo(ConsultMethod.FACE_TO_FACE);
        assertThat(saved.getBranchName()).isEqualTo("신한투자증권 PWM센터");
        assertThat(saved.getCounselorName()).isEqualTo("김신한 PB팀장");
        assertThat(saved.getPlanId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("RESERVED");
        assertThat(response.hasSummary()).isFalse();
    }

    @Test
    void createWithNullTypeThrows() {
        assertThatThrownBy(() -> service.create(1L,
                new ConsultationCreateRequest(null, LocalDateTime.now(), null)))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    void createWithPastScheduleThrows() {
        assertThatThrownBy(() -> service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().minusDays(1), null)))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    void changeScheduleWithPastThrows() {
        assertThatThrownBy(() -> service.changeSchedule(1L, 10L,
                new ConsultationScheduleUpdateRequest(LocalDateTime.now().minusHours(1))))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    void updateMemoTooLongThrows() {
        String tooLong = "a".repeat(1001);
        assertThatThrownBy(() -> service.updateMemo(1L, 10L,
                new ConsultationMemoUpdateRequest(tooLong)))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    void getMyConsultationsUsesBatchSummaryLookup() {
        when(consultationRepository.findByUserIdOrderByScheduledAtDesc(1L))
                .thenReturn(List.of(reservedConsultation(), reservedConsultation()));
        when(summaryRepository.findByConsultationIdIn(any())).thenReturn(List.of());

        service.getMyConsultations(1L);

        verify(summaryRepository, times(1)).findByConsultationIdIn(any());
        verify(summaryRepository, never()).findByConsultationId(any());
    }

    @Test
    void cancelReservedSetsCancelled() {
        Consultation reserved = reservedConsultation();
        when(consultationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(reserved));

        service.cancel(1L, 10L);

        assertThat(reserved.getStatus()).isEqualTo(ConsultStatus.CANCELLED);
    }

    @Test
    void cancelNonReservedThrows() {
        Consultation completed = Consultation.builder()
                .userId(1L).title("완료 상담").consultType(ConsultType.PB)
                .status(ConsultStatus.COMPLETED).scheduledAt(LocalDateTime.now().minusDays(1))
                .method(ConsultMethod.ONLINE).build();
        when(consultationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.cancel(1L, 10L))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    void getConsultationNotFoundThrows() {
        when(consultationRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyConsultation(1L, 99L))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONSULTATION_NOT_FOUND);
    }

    @Test
    void getSummaryWithoutSummaryThrows() {
        Consultation completed = Consultation.builder()
                .userId(1L).title("완료 상담").consultType(ConsultType.PB)
                .status(ConsultStatus.COMPLETED).scheduledAt(LocalDateTime.now().minusDays(1))
                .method(ConsultMethod.ONLINE).build();
        when(consultationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(completed));
        when(summaryRepository.findByConsultationId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary(1L, 10L))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONSULTATION_SUMMARY_NOT_FOUND);
    }

    @Test
    void seedCreatesReservedAndTwoCompletedWithOneSummary() {
        when(consultationRepository.findByUserId(1L)).thenReturn(List.of());
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ConsultationResponse> result = service.seed(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).status()).isEqualTo("RESERVED");
        assertThat(result.get(1).status()).isEqualTo("COMPLETED");
        assertThat(result.get(1).hasSummary()).isTrue();
        assertThat(result.get(2).hasSummary()).isFalse();
        verify(consultationRepository, times(3)).save(any(Consultation.class));
        verify(summaryRepository).save(any());
    }

    private Consultation reservedConsultation() {
        return Consultation.builder()
                .userId(1L).title("예약 상담").consultType(ConsultType.PB)
                .status(ConsultStatus.RESERVED).scheduledAt(LocalDateTime.now().plusDays(2))
                .method(ConsultMethod.FACE_TO_FACE).branchName("신한투자증권 PWM센터")
                .counselorName("김신한 PB팀장").build();
    }
}
