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
}
