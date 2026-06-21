package com.sol.user.survey.mapper;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.survey.dto.SurveyAnswerResponse;
import com.sol.user.survey.dto.SurveySaveRequest;
import com.sol.user.survey.entity.SurveyResponse;
import com.sol.user.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SurveyMapper {

    private static final String Q1_CODE = "MONTHLY_SALARY_Q1";
    private static final String Q2_CODE = "MONTHLY_SALARY_Q2";
    private static final String Q3_CODE = "MONTHLY_SALARY_Q3";

    public List<SurveyResponse> toEntities(User user, SurveySaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                toEntity(user, Q1_CODE, request.getQ1(), now),
                toEntity(user, Q2_CODE, request.getQ2(), now),
                toEntity(user, Q3_CODE, request.getQ3(), now)
        );
    }

    public SurveyAnswerResponse toResponse(List<SurveyResponse> responses) {
        Map<String, Integer> answerMap = responses.stream()
                .collect(Collectors.toMap(
                        SurveyResponse::getQuestionCode,
                        r -> Integer.parseInt(r.getAnswerValue()),
                        (existing, duplicate) -> existing
                ));

        if (!answerMap.containsKey(Q1_CODE) || !answerMap.containsKey(Q2_CODE) || !answerMap.containsKey(Q3_CODE)) {
            throw new BaseException(ErrorCode.SURVEY_NOT_FOUND);
        }

        return SurveyAnswerResponse.builder()
                .q1(answerMap.get(Q1_CODE))
                .q2(answerMap.get(Q2_CODE))
                .q3(answerMap.get(Q3_CODE))
                .build();
    }

    private SurveyResponse toEntity(User user, String questionCode, int answerValue, LocalDateTime now) {
        return SurveyResponse.builder()
                .user(user)
                .questionCode(questionCode)
                .answerValue(String.valueOf(answerValue))
                .answeredAt(now)
                .build();
    }
}
