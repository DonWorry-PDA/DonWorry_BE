package com.sol.user.notification.provider;

import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.notification.dto.NotificationTarget;
import com.sol.user.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PensionDepositNotificationProvider implements NotificationProvider {

    private final CashFlowEventRepository cashFlowEventRepository;

    @Override
    public NotificationType getType() {
        return NotificationType.PENSION_DEPOSIT;
    }

    @Override
    public List<NotificationTarget> findTargets() {
        LocalDate today = LocalDate.now();
        return cashFlowEventRepository
                .findUserIdAndTotalAmountByEventTypeAndDate("PENSION", today)
                .stream()
                .map(row -> {
                    Long userId = (Long) row[0];
                    BigDecimal amount = (BigDecimal) row[1];
                    return new NotificationTarget(
                            userId,
                            "연금 입금",
                            String.format("%,d원이 입금되었어요.", amount.longValue()),
                            "/calendar"
                    );
                })
                .toList();
    }
}
