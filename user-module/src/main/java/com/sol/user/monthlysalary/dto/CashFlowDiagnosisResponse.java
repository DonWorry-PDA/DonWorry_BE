package com.sol.user.monthlysalary.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CashFlowDiagnosisResponse {
    private BigDecimal monthlyCashFlow;          // 현재 월 현금흐름 합 (국민연금 + 배당 + 예금이자)
    private BigDecimal nationalPension;          // 국민연금 월 수령액
    private BigDecimal dividendIncome;           // 배당 ETF 월 분배금
    private BigDecimal interestIncome;           // 예금 이자 (실수령, net)
    private BigDecimal targetMonthlyLivingCost;  // 목표 생활비
    private BigDecimal monthlyShortfall;         // 매달 부족한 돈 (= max(목표 − 현금흐름, 0))
    private boolean shortfallExists;             // 부족액 존재 여부 (화면 분기용)
}
