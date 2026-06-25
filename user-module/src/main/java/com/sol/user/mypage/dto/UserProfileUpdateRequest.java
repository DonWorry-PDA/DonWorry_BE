package com.sol.user.mypage.dto;

import java.math.BigDecimal;

public record UserProfileUpdateRequest(
        Integer age,
        String status,
        String pensionStatus,
        BigDecimal monthlyTargetKrw
) {
    public Boolean retiredValue() {
        if (status == null) return null;
        return "은퇴 후".equals(status);
    }

    public Boolean nationalPensionReceivingValue() {
        if (pensionStatus == null) return null;
        return "수령 중".equals(pensionStatus);
    }
}
