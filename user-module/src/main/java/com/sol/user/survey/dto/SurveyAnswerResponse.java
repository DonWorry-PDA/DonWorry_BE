package com.sol.user.survey.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SurveyAnswerResponse {
    private int q1;
    private int q2;
    private int q3;
}
