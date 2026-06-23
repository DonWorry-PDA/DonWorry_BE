package com.sol.user.calendar.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.calendar.dto.CalendarMonthResponse;
import com.sol.user.calendar.service.CalendarQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Calendar", description = "사용자 캘린더 조회 API")
@RestController
@RequestMapping("/api/user/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarQueryService calendarQueryService;

    @Operation(summary = "월별 캘린더 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<CalendarMonthResponse>> getMonth(
            @RequestAttribute("userId") Long userId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(ApiResponse.ok(calendarQueryService.getMonth(userId, year, month)));
    }
}
