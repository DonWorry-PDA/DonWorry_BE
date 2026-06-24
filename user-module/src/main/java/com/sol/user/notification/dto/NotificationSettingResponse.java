package com.sol.user.notification.dto;

import com.sol.user.notification.entity.NotificationSetting;

import java.util.List;

public record NotificationSettingResponse(String id, boolean enabled) {

    public static List<NotificationSettingResponse> from(NotificationSetting setting) {
        return List.of(
                new NotificationSettingResponse("balance", setting.isBalanceEnabled()),
                new NotificationSettingResponse("pension", setting.isPensionEnabled()),
                new NotificationSettingResponse("dividend", setting.isDividendEnabled()),
                new NotificationSettingResponse("report", setting.isReportEnabled())
        );
    }
}
