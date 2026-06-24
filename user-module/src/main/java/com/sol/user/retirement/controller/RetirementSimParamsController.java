package com.sol.user.retirement.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.retirement.dto.RetirementSimParamsResponse;
import com.sol.user.retirement.service.RetirementSimParamsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "은퇴 시뮬레이션", description = "은퇴 시뮬레이션 초기 파라미터 API")
@RestController
@RequestMapping("/api/user/retirement-simulation")
@RequiredArgsConstructor
public class RetirementSimParamsController {

    private final RetirementSimParamsService retirementSimParamsService;

    @Operation(summary = "은퇴 시뮬레이션 초기 파라미터 조회",
               description = "나이·총자산·목표생활비·월연금을 조합해 반환한다. 계산은 클라이언트에서 수행한다.")
    @GetMapping("/params")
    public ResponseEntity<ApiResponse<RetirementSimParamsResponse>> getParams(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(retirementSimParamsService.getParams(userId)));
    }
}
