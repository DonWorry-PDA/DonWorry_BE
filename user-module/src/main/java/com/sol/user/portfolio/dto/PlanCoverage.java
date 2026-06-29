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
    private BigDecimal monthlyIncome;              // 월수령액 (총인출: 원금소진+자본차익 포함)
    private BigDecimal alphaCoverageRate;          // α충족률 = 총인출 기준 (α≤0이면 null, 100캡)
    private BigDecimal sustainableCoverageRate;    // α충족률 = 지속가능 기준 (이자·배당만, 100캡)
    private BigDecimal inheritanceAmount;          // 상속분
    private BigDecimal shortTermLumpSum;           // 단기 목돈 (유동성안이 따로 확보한 일회성 인출분, 원금 그대로)

    // 버킷별 net 운용수입 (표시 monthlyIncome과 동일 q3·과세기준). 종목별 monthlyContribution 분배 재료.
    // 국민연금·연금저축(사적연금)은 종목 귀속에서 제외 → safeNet+riskNet = monthlyIncome − 국민연금 − 연금저축.
    private BigDecimal safeNetIncome;              // SAFE 버킷(floor+여유안전) net 월수입
    private BigDecimal riskNetIncome;              // RISK 버킷 net 월수입
}
