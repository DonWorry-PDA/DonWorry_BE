package com.sol.user.notification.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_setting")
@Getter
@NoArgsConstructor
public class NotificationSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "balance_enabled", nullable = false)
    private boolean balanceEnabled = true;

    @Column(name = "pension_enabled", nullable = false)
    private boolean pensionEnabled = true;

    @Column(name = "dividend_enabled", nullable = false)
    private boolean dividendEnabled = true;

    @Column(name = "report_enabled", nullable = false)
    private boolean reportEnabled = true;

    @Column(name = "market_open_reminder", nullable = false)
    private boolean marketOpenReminder = false;

    public static NotificationSetting defaultFor(User user) {
        NotificationSetting setting = new NotificationSetting();
        setting.user = user;
        return setting;
    }

    public void toggle(String id, boolean enabled) {
        switch (id) {
            case "balance" -> this.balanceEnabled = enabled;
            case "pension" -> this.pensionEnabled = enabled;
            case "dividend" -> this.dividendEnabled = enabled;
            case "report" -> this.reportEnabled = enabled;
        }
    }

    public void subscribeMarketOpenReminder() {
        this.marketOpenReminder = true;
    }

    public void clearMarketOpenReminder() {
        this.marketOpenReminder = false;
    }
}
