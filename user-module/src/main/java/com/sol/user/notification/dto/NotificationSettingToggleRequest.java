package com.sol.user.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingToggleRequest(@NotNull Boolean enabled) {
}
