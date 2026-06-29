package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.BucketRole;
import com.sol.user.portfolio.type.CurrencyExposure;

import java.math.BigDecimal;

/**
 * 안 내 개별 보유 종목. STEP5에서 위험·안전·단기버킷 모두 개별 종목으로 분해된다(role로 버킷 구분).
 *
 * <p>{@code monthlyContribution}은 STEP6 산출값이라 배분(STEP5) 단계에선 알 수 없다 → 생성 시 null,
 * 추천 응답 조립(PortfolioRecommendationMapper)에서 버킷 net 운용수입을 버킷 내 비중으로 분배해 채운다.
 */
public record Holding(
        Long productId,
        String ticker,
        String productName,
        BucketRole role,
        CurrencyExposure currency,
        BigDecimal weight,   // 버킷 내 비중 (각 버킷 합=1.0)
        BigDecimal amount,   // 금액
        BigDecimal monthlyContribution // 종목별 월기여 (원/월, net 실수령 — monthlyIncome과 동일 과세기준). 배분단계 null, 매퍼에서 채움
) {

    /** 배분(STEP5) 단계 생성 — 월기여는 STEP6 매핑에서 채워지므로 null. */
    public static Holding of(Long productId, String ticker, String productName, BucketRole role,
                             CurrencyExposure currency, BigDecimal weight, BigDecimal amount) {
        return new Holding(productId, ticker, productName, role, currency, weight, amount, null);
    }

    /** 금액만 교체한 사본(순매수 차감·스케일링용). 월기여 등 나머지는 유지. */
    public Holding withAmount(BigDecimal newAmount) {
        return new Holding(productId, ticker, productName, role, currency, weight, newAmount, monthlyContribution);
    }

    /** 종목별 월기여만 채운 사본(추천 응답 조립 최종 패스용). */
    public Holding withMonthlyContribution(BigDecimal contribution) {
        return new Holding(productId, ticker, productName, role, currency, weight, amount, contribution);
    }
}
