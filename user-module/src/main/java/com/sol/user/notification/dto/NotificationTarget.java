package com.sol.user.notification.dto;

public record NotificationTarget(
        Long userId,
        String title,
        String content,
        String linkTarget
) {}
