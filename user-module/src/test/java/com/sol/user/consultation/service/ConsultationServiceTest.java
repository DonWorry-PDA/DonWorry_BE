package com.sol.user.consultation.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.consultation.dto.ConsultationCreateRequest;
import com.sol.user.consultation.dto.ConsultationMemoUpdateRequest;
import com.sol.user.consultation.dto.ConsultationResponse;
import com.sol.user.consultation.dto.ConsultationScheduleUpdateRequest;
import com.sol.user.branch.entity.Branch;
import com.sol.user.branch.repository.BranchRepository;
import com.sol.user.branch.type.Institution;
import com.sol.user.consultation.entity.Consultation;
import com.sol.user.consultation.repository.ConsultationRepository;
import com.sol.user.consultation.repository.ConsultationSummaryRepository;
import com.sol.user.consultation.type.ConsultMethod;
import com.sol.user.monthlysalary.repository.SalaryPlanRepository;
import com.sol.user.consultation.type.ConsultStatus;
import com.sol.user.consultation.type.ConsultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
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
    private SalaryPlanRepository salaryPlanRepository;
    private BranchRepository branchRepository;
    private ConsultationService service;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationRepository.class);
        summaryRepository = mock(ConsultationSummaryRepository.class);
        salaryPlanRepository = mock(SalaryPlanRepository.class);
        branchRepository = mock(BranchRepository.class);
        service = new ConsultationService(consultationRepository, summaryRepository,
                salaryPlanRepository, branchRepository);
    }

    @Test
    void createAssignsReservedStatusAndTypeDefaults() {
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(salaryPlanRepository.existsByPlanIdAndUserUserId(7L, 1L)).thenReturn(true);

        ConsultationResponse response = service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), 7L, null, null,
                        null, null));

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
    void createWithoutTopicUsesTypeDefaultTitle() {
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), null, null, null,
                        null, null));

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo(ConsultType.PB.getDefaultTitle());
    }

    @Test
    void createWithTopicAndContextTopicsStoresThem() {
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConsultationResponse response = service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), null,
                        "국민연금 연기 비교 상담",
                        List.of("연기율별 수령액 비교", "연기 시 손익분기 시점"), null, null));

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        Consultation saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("국민연금 연기 비교 상담");
        assertThat(saved.getContextTopics()).containsExactly("연기율별 수령액 비교", "연기 시 손익분기 시점");
        assertThat(response.contextTopics()).containsExactly("연기율별 수령액 비교", "연기 시 손익분기 시점");
    }

    @Test
    void createDropsBlankContextTopics() {
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), null, null,
                        Arrays.asList("연기율별 수령액 비교", "  ", null), null, null));

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getContextTopics()).containsExactly("연기율별 수령액 비교");
    }

    @Test
    void createWithTooLongTopicThrows() {
        assertThatThrownBy(() -> service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), null,
                        "a".repeat(101), null, null, null)))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    void createWithTooManyContextTopicsThrows() {
        List<String> tooMany = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> "주제" + i).toList();
        assertThatThrownBy(() -> service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), null, null, tooMany,
                        null, null)))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    void createWithTooLongContextTopicThrows() {
        assertThatThrownBy(() -> service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), null, null,
                        List.of("a".repeat(201)), null, null)))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    void createWithUnownedPlanThrows() {
        when(salaryPlanRepository.existsByPlanIdAndUserUserId(99L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), 99L, null, null,
                        null, null)))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        verify(consultationRepository, never()).save(any(Consultation.class));
    }

    @Test
    void createWithBranchIdSavesSelectedBranch() {
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Branch branch = Branch.builder()
                .institution(Institution.SHINHAN_SECURITIES)
                .name("신한투자증권 강남금융센터")
                .latitude(37.5).longitude(127.0).build();
        when(branchRepository.findById(42L)).thenReturn(Optional.of(branch));

        ConsultationResponse response = service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), null, null, null,
                        42L, ConsultMethod.PHONE));

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        Consultation saved = captor.getValue();
        assertThat(saved.getBranchId()).isEqualTo(42L);
        assertThat(saved.getBranchName()).isEqualTo("신한투자증권 강남금융센터");
        assertThat(saved.getMethod()).isEqualTo(ConsultMethod.PHONE);
        // 전화 상담이라도 지점을 골랐으면 location은 지점명으로 내려간다.
        assertThat(response.location()).isEqualTo("신한투자증권 강남금융센터");
    }

    @Test
    void createWithBankBranchPrefixesInstitutionInStoredName() {
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 은행 원본 지점명엔 '신한은행' 접두어가 없다.
        Branch branch = Branch.builder()
                .institution(Institution.SHINHAN_BANK)
                .name("가락동금융센터")
                .latitude(37.5).longitude(127.0).build();
        when(branchRepository.findById(7L)).thenReturn(Optional.of(branch));

        service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), null, null, null,
                        7L, ConsultMethod.FACE_TO_FACE));

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        // 내역 표시명이 FE 지점 선택 화면과 일치하도록 '신한은행 …'으로 저장된다.
        assertThat(captor.getValue().getBranchName()).isEqualTo("신한은행 가락동금융센터");
    }

    @Test
    void createWithUnknownBranchIdThrows() {
        when(branchRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().plusDays(3), null, null, null,
                        404L, null)))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        verify(consultationRepository, never()).save(any(Consultation.class));
    }

    @Test
    void createWithNullTypeThrows() {
        assertThatThrownBy(() -> service.create(1L,
                new ConsultationCreateRequest(null, LocalDateTime.now(), null, null, null, null, null)))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    void createWithPastScheduleThrows() {
        assertThatThrownBy(() -> service.create(1L,
                new ConsultationCreateRequest(ConsultType.PB, LocalDateTime.now().minusDays(1), null, null, null,
                        null, null)))
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
                .method(ConsultMethod.PHONE).build();
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
                .method(ConsultMethod.PHONE).build();
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
