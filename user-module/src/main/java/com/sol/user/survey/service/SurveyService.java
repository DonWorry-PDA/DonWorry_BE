package com.sol.user.survey.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.survey.dto.SurveyAnswerResponse;
import com.sol.user.survey.dto.SurveySaveRequest;
import com.sol.user.survey.entity.SurveyResponse;
import com.sol.user.survey.mapper.SurveyMapper;
import com.sol.user.survey.repository.SurveyResponseRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyResponseRepository surveyResponseRepository;
    private final UserRepository userRepository;
    private final SurveyMapper surveyMapper;

    @Transactional
    public SurveyAnswerResponse save(Long userId, SurveySaveRequest request) {
        validate(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        surveyResponseRepository.deleteAllByUser(user);

        List<SurveyResponse> saved = surveyResponseRepository.saveAll(
                surveyMapper.toEntities(user, request)
        );

        return surveyMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SurveyAnswerResponse get(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        List<SurveyResponse> responses = surveyResponseRepository.findByUser(user);

        if (responses.isEmpty()) {
            throw new BaseException(ErrorCode.SURVEY_NOT_FOUND);
        }

        return surveyMapper.toResponse(responses);
    }

    /**
     * 객관식 답변 범위 검증 — 운용등급/소진모델 계산기가 기대하는 인덱스 범위와 일치해야 한다.
     * (q1 손실감내 0~3, q2 현금흐름vs성장 0~2, q3 상속vs소비 0~2)
     * 신뢰경계(REST)에서 막아, 잘못된 값이 추천 단계까지 흘러가 늦게 터지는 것을 방지.
     */
    private void validate(SurveySaveRequest request) {
        if (request == null
                || outOfRange(request.getQ1(), 3)
                || outOfRange(request.getQ2(), 2)
                || outOfRange(request.getQ3(), 2)) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean outOfRange(int value, int max) {
        return value < 0 || value > max;
    }
}
