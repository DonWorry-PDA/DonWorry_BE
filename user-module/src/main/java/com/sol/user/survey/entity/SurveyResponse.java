package com.sol.user.survey.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "survey_response")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "response_id")
    private Long responseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "question_code", nullable = false, length = 40)
    private String questionCode;

    @Column(name = "answer_value", length = 40)
    private String answerValue;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
