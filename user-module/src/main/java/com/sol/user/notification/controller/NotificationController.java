package com.sol.user.notification.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.notification.dto.NotificationResponse;
import com.sol.user.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/user/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "SSE 알림 구독", description = "실시간 알림 수신을 위한 SSE 연결")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestAttribute("userId") Long userId) {
        return notificationService.subscribe(userId);
    }

    @Operation(summary = "내 알림 목록 조회", description = "최신순으로 반환")
    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(notificationService.getNotifications(userId));
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable Long notificationId,
            @RequestAttribute("userId") Long userId) {
        notificationService.markAsRead(notificationId, userId);
        return ApiResponse.ok(null);
    }
}
