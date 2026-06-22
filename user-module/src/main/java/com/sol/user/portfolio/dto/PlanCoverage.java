package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.PlanType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 한 안의 STEP6 결과: 월수령액·α충족률·상속분.
 */
@Getter
@Builder
public class PlanCoverage {

    private PlanType type;
    private BigDecimal monthlyIncome;       // 월수령액
    private BigDecimal alphaCoverageRate;   // α충족률 (α≤0이면 null — 미적용)
    private BigDecimal inheritanceAmount;   // 상속분
}
