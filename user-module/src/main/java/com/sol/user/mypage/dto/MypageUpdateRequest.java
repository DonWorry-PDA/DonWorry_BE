package com.sol.user.mypage.dto;

import java.math.BigDecimal;

public record MypageUpdateRequest(
        Integer age,
        Boolean retired,
        Boolean nationalPensionReceiving,
        BigDecimal monthlyTargetLivingCost
) {
}
