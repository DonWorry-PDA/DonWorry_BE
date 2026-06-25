package com.sol.user.consultation.entity;

import com.sol.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 완료 상담의 PB 작성 요약(시드). Consultation과 1:1.
 */
@Entity
@Table(name = "consultation_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultationSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consultation_summary_id")
    private Long id;

    @Column(name = "consultation_id", nullable = false, unique = true)
    private Long consultationId;

    @Column(name = "diagnosis", length = 1000)
    private String diagnosis;

    @ElementCollection
    @CollectionTable(
            name = "consultation_summary_recommendation",
            joinColumns = @JoinColumn(name = "consultation_summary_id"))
    @Column(name = "content", length = 500)
    private List<String> recommendations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "consultation_summary_next_step",
            joinColumns = @JoinColumn(name = "consultation_summary_id"))
    @Column(name = "content", length = 500)
    private List<String> nextSteps = new ArrayList<>();

    @Builder
    private ConsultationSummary(Long consultationId, String diagnosis,
                               List<String> recommendations, List<String> nextSteps) {
        this.consultationId = consultationId;
        this.diagnosis = diagnosis;
        this.recommendations = recommendations == null ? new ArrayList<>() : new ArrayList<>(recommendations);
        this.nextSteps = nextSteps == null ? new ArrayList<>() : new ArrayList<>(nextSteps);
    }
}
