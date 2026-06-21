package com.sol.user.survey.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.survey.dto.SurveyAnswerResponse;
import com.sol.user.survey.dto.SurveySaveRequest;
import com.sol.user.survey.service.SurveyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Monthly Salary Survey", description = "월급만들기 설문 API")
@RestController
@RequestMapping("/api/user/monthly-salary/survey")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @Operation(summary = "투자 성향 설문 저장")
    @PostMapping
    public ResponseEntity<ApiResponse<SurveyAnswerResponse>> saveSurvey(
            @RequestAttribute("userId") Long userId,
            @RequestBody SurveySaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(surveyService.save(userId, request)));
    }

    @Operation(summary = "투자 성향 설문 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<SurveyAnswerResponse>> getSurvey(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(surveyService.get(userId)));
    }
}
