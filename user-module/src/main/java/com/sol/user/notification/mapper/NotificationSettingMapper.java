package com.sol.user.notification.mapper;

import com.sol.user.notification.dto.MarketOpenReminderResponse;
import com.sol.user.notification.dto.NotificationSettingResponse;
import com.sol.user.notification.entity.NotificationSetting;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationSettingMapper {

    public List<NotificationSettingResponse> toSettingResponses(NotificationSetting setting) {
        return List.of(
                new NotificationSettingResponse("balance", setting.isBalanceEnabled()),
                new NotificationSettingResponse("pension", setting.isPensionEnabled()),
                new NotificationSettingResponse("dividend", setting.isDividendEnabled()),
                new NotificationSettingResponse("report", setting.isReportEnabled())
        );
    }

    public MarketOpenReminderResponse toMarketOpenReminderResponse(NotificationSetting setting) {
        return new MarketOpenReminderResponse(setting.isMarketOpenReminder());
    }
}
