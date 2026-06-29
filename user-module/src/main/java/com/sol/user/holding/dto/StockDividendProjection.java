package com.sol.user.holding.dto;

import java.math.BigDecimal;

/**
 * 사용자가 보유한 개별주식의 종목별 평가액·배당수익률. 투자 건강검진 성장블록의 "현재 배당" 산출에 쓴다.
 * 한 종목을 여러 계좌/건으로 나눠 들고 있어도 product 단위로 합산(SUM)된다.
 */
public interface StockDividendProjection {

    Long getProductId();

    String getProductName();

    /** 종목별 보유 수량 합. 평가액은 quantity × 실시간 현재가로 산출(DB evaluationAmount 미사용). */
    BigDecimal getQuantity();

    /** stock_detail.dividend_yield — 시가배당률(%), 무배당은 0. 미적재면 NULL. */
    BigDecimal getDividendYield();

    /** stock_detail.sector — 산업 분류(섹터 쏠림 진단용). 미적재면 NULL. */
    String getSector();
}
