package com.sol.user.notification.dto;

import com.sol.user.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TestNotifyRequest(
        @NotNull Long targetUserId,
        @NotNull NotificationType type,
        @NotBlank String title,
        @NotBlank String content
) {}
