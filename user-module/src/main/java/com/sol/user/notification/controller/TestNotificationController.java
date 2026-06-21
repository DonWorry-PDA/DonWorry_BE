package com.sol.user.notification.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.notification.dto.TestNotifyRequest;
import com.sol.user.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Test", description = "개발용 테스트 API")
@RestController
@RequestMapping("/api/user/test")
@RequiredArgsConstructor
public class TestNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 수동 발송 (개발용)", description = "targetUserId에게 알림 DB 저장 + SSE 전송")
    @PostMapping("/notify")
    public ApiResponse<Void> sendTestNotification(@Valid @RequestBody TestNotifyRequest request) {
        notificationService.notify(
                request.targetUserId(),
                request.type(),
                request.title(),
                request.content()
        );
        return ApiResponse.ok(null);
    }
}
