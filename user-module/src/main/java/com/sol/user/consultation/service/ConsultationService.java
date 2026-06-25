package com.sol.user.consultation.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.consultation.dto.ConsultationCreateRequest;
import com.sol.user.consultation.dto.ConsultationMemoUpdateRequest;
import com.sol.user.consultation.dto.ConsultationResponse;
import com.sol.user.consultation.dto.ConsultationScheduleUpdateRequest;
import com.sol.user.consultation.dto.ConsultationSummaryResponse;
import com.sol.user.consultation.entity.Consultation;
import com.sol.user.consultation.entity.ConsultationSummary;
import com.sol.user.consultation.repository.ConsultationRepository;
import com.sol.user.consultation.repository.ConsultationSummaryRepository;
import com.sol.user.consultation.type.ConsultMethod;
import com.sol.user.consultation.type.ConsultStatus;
import com.sol.user.consultation.type.ConsultType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationService {

    private static final int MAX_MEMO_LENGTH = 1000;

    private final ConsultationRepository consultationRepository;
    private final ConsultationSummaryRepository consultationSummaryRepository;

    /** 예약 생성 — 유형별 기본 방식/지점/상담원을 배정한다. */
    @Transactional
    public ConsultationResponse create(Long userId, ConsultationCreateRequest request) {
        if (request == null || request.consultType() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        requireFutureSchedule(request.scheduledAt());
        ConsultType type = request.consultType();
        ConsultMethod method = defaultMethod(type);

        Consultation saved = consultationRepository.save(Consultation.builder()
                .userId(userId)
                .title(type.getDefaultTitle())
                .consultType(type)
                .status(ConsultStatus.RESERVED)
                .scheduledAt(request.scheduledAt())
                .method(method)
                .branchName(method == ConsultMethod.FACE_TO_FACE ? defaultBranch(type) : null)
                .counselorName(defaultCounselor(type))
                .planId(request.planId())
                .build());

        return ConsultationResponse.from(saved, false);
    }

    public List<ConsultationResponse> getMyConsultations(Long userId) {
        List<Consultation> consultations = consultationRepository.findByUserIdOrderByScheduledAtDesc(userId);
        Set<Long> withSummary = summarizedConsultationIds(consultations);
        return consultations.stream()
                .map(c -> ConsultationResponse.from(c, withSummary.contains(c.getId())))
                .toList();
    }

    public ConsultationResponse getMyConsultation(Long userId, Long consultationId) {
        Consultation consultation = findOwned(userId, consultationId);
        boolean hasSummary = consultationSummaryRepository.findByConsultationId(consultationId).isPresent();
        return ConsultationResponse.from(consultation, hasSummary);
    }

    @Transactional
    public ConsultationResponse changeSchedule(Long userId, Long consultationId,
                                               ConsultationScheduleUpdateRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        requireFutureSchedule(request.scheduledAt());
        Consultation consultation = findOwned(userId, consultationId);
        consultation.changeSchedule(request.scheduledAt());
        boolean hasSummary = consultationSummaryRepository.findByConsultationId(consultationId).isPresent();
        return ConsultationResponse.from(consultation, hasSummary);
    }

    @Transactional
    public void cancel(Long userId, Long consultationId) {
        Consultation consultation = findOwned(userId, consultationId);
        consultation.cancel();
    }

    public ConsultationSummaryResponse getSummary(Long userId, Long consultationId) {
        Consultation consultation = findOwned(userId, consultationId);
        ConsultationSummary summary = consultationSummaryRepository.findByConsultationId(consultationId)
                .orElseThrow(() -> new BaseException(ErrorCode.CONSULTATION_SUMMARY_NOT_FOUND));
        return ConsultationSummaryResponse.of(summary, consultation.getUserMemo());
    }

    @Transactional
    public void updateMemo(Long userId, Long consultationId, ConsultationMemoUpdateRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        if (request.memo() != null && request.memo().length() > MAX_MEMO_LENGTH) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        Consultation consultation = findOwned(userId, consultationId);
        consultation.updateMemo(request.memo());
    }

    /** 시연용 본인 데모 상담 시드 — 기존 본인 상담을 지우고 예약 1 + 완료 2(요약 1)를 생성한다. */
    @Transactional
    public List<ConsultationResponse> seed(Long userId) {
        deleteAll(userId);
        LocalDateTime now = LocalDateTime.now();

        Consultation reserved = consultationRepository.save(Consultation.builder()
                .userId(userId)
                .title("은퇴 자산 설계 상담")
                .consultType(ConsultType.PB)
                .status(ConsultStatus.RESERVED)
                .scheduledAt(now.plusDays(5).with(LocalTime.of(14, 0)))
                .method(ConsultMethod.FACE_TO_FACE)
                .branchName("신한투자증권 PWM센터")
                .counselorName("김신한 PB팀장")
                .build());

        Consultation completedWithSummary = consultationRepository.save(Consultation.builder()
                .userId(userId)
                .title("연금 수령 전략 상담")
                .consultType(ConsultType.PB)
                .status(ConsultStatus.COMPLETED)
                .scheduledAt(now.minusDays(34).with(LocalTime.of(10, 30)))
                .method(ConsultMethod.ONLINE)
                .counselorName("김신한 PB팀장")
                .build());

        consultationSummaryRepository.save(ConsultationSummary.builder()
                .consultationId(completedWithSummary.getId())
                .diagnosis("보유 자산의 약 70%가 예금·현금성에 집중돼 있어요. 인출 단계에서 물가 상승을 방어할 성장 자산이 부족할 수 있습니다.")
                .recommendations(List.of(
                        "생활비 3년치는 예금·MMF로 유지해 '안전 바닥' 확보",
                        "나머지는 배당 ETF·채권 혼합으로 단계적 이전",
                        "국민연금 1년 연기 시 수령액 약 7.2% 증액 검토"))
                .nextSteps(List.of(
                        "대면 상담에서 계좌별 인출 순서 확정",
                        "IRP 추가 납입 한도 점검"))
                .build());

        Consultation completedNoSummary = consultationRepository.save(Consultation.builder()
                .userId(userId)
                .title("국민연금 연기 비교 상담")
                .consultType(ConsultType.PB)
                .status(ConsultStatus.COMPLETED)
                .scheduledAt(now.minusDays(78).with(LocalTime.of(15, 0)))
                .method(ConsultMethod.ONLINE)
                .counselorName("김신한 PB팀장")
                .build());

        return List.of(
                ConsultationResponse.from(reserved, false),
                ConsultationResponse.from(completedWithSummary, true),
                ConsultationResponse.from(completedNoSummary, false)
        );
    }

    private void deleteAll(Long userId) {
        List<Consultation> existing = consultationRepository.findByUserId(userId);
        if (existing.isEmpty()) {
            return;
        }
        List<Long> ids = existing.stream().map(Consultation::getId).toList();
        consultationSummaryRepository.deleteByConsultationIdIn(ids);
        consultationRepository.deleteAll(existing);
    }

    private Consultation findOwned(Long userId, Long consultationId) {
        return consultationRepository.findByIdAndUserId(consultationId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.CONSULTATION_NOT_FOUND));
    }

    private Set<Long> summarizedConsultationIds(List<Consultation> consultations) {
        if (consultations.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = consultations.stream().map(Consultation::getId).toList();
        // N+1 방지: 상담별 조회 대신 한 번에 요약을 가져온다.
        return consultationSummaryRepository.findByConsultationIdIn(ids).stream()
                .map(ConsultationSummary::getConsultationId)
                .collect(Collectors.toSet());
    }

    private void requireFutureSchedule(LocalDateTime scheduledAt) {
        if (scheduledAt == null || scheduledAt.isBefore(LocalDateTime.now())) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private ConsultMethod defaultMethod(ConsultType type) {
        return type == ConsultType.PB ? ConsultMethod.FACE_TO_FACE : ConsultMethod.ONLINE;
    }

    private String defaultBranch(ConsultType type) {
        return type == ConsultType.PB ? "신한투자증권 PWM센터" : null;
    }

    private String defaultCounselor(ConsultType type) {
        return type == ConsultType.PB ? "김신한 PB팀장" : "신한라이프 상담매니저";
    }
}
