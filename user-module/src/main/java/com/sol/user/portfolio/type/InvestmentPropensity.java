package com.sol.user.portfolio.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InvestmentPropensity {
    AGGRESSIVE("공격투자형", 5),
    ACTIVE("적극투자형", 4),
    NEUTRAL("위험중립형", 3),
    STABLE_SEEKING("안정추구형", 2),
    STABLE("안정형", 1);

    private final String label;
    private final int gradeLimit;
}
