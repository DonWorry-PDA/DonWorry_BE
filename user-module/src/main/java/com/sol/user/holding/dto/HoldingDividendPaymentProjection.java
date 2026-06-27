package com.sol.user.holding.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 보유 ETF의 실제 지급된 분배금(확정) 1건 — 캘린더 확정 분배금 표시용(#219).
 * 미래 투영({@link HoldingDividendCalendarProjection})과 달리 dividend_history의 실지급 행을 그대로 쓴다.
 */
public interface HoldingDividendPaymentProjection {

    Long getProductId();

    String getProductName();

    BigDecimal getQuantity();

    BigDecimal getAmountPerUnit();

    LocalDate getPaymentDate();
}
