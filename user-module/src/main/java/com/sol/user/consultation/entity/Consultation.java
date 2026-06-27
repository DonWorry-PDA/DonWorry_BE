package com.sol.user.consultation.entity;

import com.sol.common.entity.BaseEntity;
import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.consultation.type.ConsultMethod;
import com.sol.user.consultation.type.ConsultStatus;
import com.sol.user.consultation.type.ConsultType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "consultation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consultation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consultation_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "consult_type", length = 20, nullable = false)
    private ConsultType consultType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ConsultStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20, nullable = false)
    private ConsultMethod method;

    @Column(name = "branch_name", length = 100)
    private String branchName;

    @Column(name = "counselor_name", length = 50)
    private String counselorName;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "user_memo", length = 1000)
    private String userMemo;

    /** 신청 맥락에서 "이번 상담에서 다룰 내용" — FE가 보낸 문자열을 그대로 저장한다. */
    @ElementCollection
    @CollectionTable(
            name = "consultation_context_topic",
            joinColumns = @JoinColumn(name = "consultation_id"))
    @Column(name = "content", length = 200)
    private List<String> contextTopics = new ArrayList<>();

    @Builder
    private Consultation(Long userId, String title, ConsultType consultType, ConsultStatus status,
                         LocalDateTime scheduledAt, ConsultMethod method, String branchName,
                         String counselorName, Long planId, String userMemo,
                         List<String> contextTopics) {
        this.userId = userId;
        this.title = title;
        this.consultType = consultType;
        this.status = status;
        this.scheduledAt = scheduledAt;
        this.method = method;
        this.branchName = branchName;
        this.counselorName = counselorName;
        this.planId = planId;
        this.userMemo = userMemo;
        this.contextTopics = contextTopics == null ? new ArrayList<>() : new ArrayList<>(contextTopics);
    }

    /** 일정 변경 — 예약 상태에서만 가능. */
    public void changeSchedule(LocalDateTime newScheduledAt) {
        requireReserved();
        if (newScheduledAt == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        this.scheduledAt = newScheduledAt;
    }

    /** 예약 취소 — 예약 상태에서만 가능. */
    public void cancel() {
        requireReserved();
        this.status = ConsultStatus.CANCELLED;
    }

    /** 내 메모 저장(사용자 작성). 상태와 무관하게 허용. */
    public void updateMemo(String memo) {
        this.userMemo = memo;
    }

    private void requireReserved() {
        if (this.status != ConsultStatus.RESERVED) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }
}
