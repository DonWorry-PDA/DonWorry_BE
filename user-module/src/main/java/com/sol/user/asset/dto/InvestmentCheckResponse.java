package com.sol.user.asset.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * 투자 건강검진(#2) 상세 응답.
 *
 * <p>헤드라인은 "자산의 N%가 현금을 만들고, 나머지는 불리거나(성장) 묶여 있어요(연금)" 프레임이다.
 * 자산을 4역할(현금흐름·성장·잠자는 돈·연금)로 나눠, "X%만 월급을 만든다"는 비난 톤 대신 각 돈의 직무를
 * 존중한다. 역할 분해는 {@link AssetBreakdown}의 5개 구성을 합이 grossTotal과 일치하도록 재배열한 것이다.
 *
 * <p>개별주는 월급 재료에서 빠졌지만 버리지 않고 "성장(자본차익) 직무"를 부여해 {@code growthAsset} 블록으로
 * 보여주고, 일부를 배당형으로 옮기면 생기는 월 현금흐름을 CTA로 제시해 월급 만들기로 유도한다.
 */
@Builder
public record InvestmentCheckResponse(
        /** 헤드라인 % — 현금흐름 자산(비연금 비STOCK 보유) / 순자산. 자산 없으면 0. */
        int cashflowAssetRatio,
        /** 순자산(grossTotal) — 4역할 금액의 합. */
        BigDecimal totalAsset,
        /** 4역할(현금흐름·성장·잠자는 돈·연금) 분해. 금액 0인 역할은 제외, ratio 합 = 100. */
        List<RoleContribution> roles,
        /** 개별주 성장 블록. 개별주 보유가 없으면 null. */
        GrowthAsset growthAsset
) {

    /** 자산 역할별 기여. monthlyCashflow는 현금흐름 역할만 추정액, 그 외는 0. */
    @Builder
    public record RoleContribution(
            String role,                  // CASHFLOW | GROWTH | IDLE | PENSION
            String label,                 // 현금흐름 / 성장 / 잠자는 돈 / 연금
            BigDecimal amount,
            int ratio,                    // 순자산 대비 %, 합 100
            BigDecimal monthlyCashflow,   // 월 현금흐름 추정액(현금흐름 역할만 > 0)
            String note                   // 역할 설명 힌트(FE 오버라이드 가능)
    ) {
    }

    /**
     * 개별주 "성장에 베팅한 자산" 블록.
     * concentration은 최대 단일종목 평가액 / 개별주 합. dividend는 종목 dividend_yield 미적재라 "거의 없음" 고정.
     */
    @Builder
    public record GrowthAsset(
            BigDecimal amount,                       // 개별주 평가액 합(stockHoldingValue)
            String topStockName,                     // 최대 비중 종목명
            int concentrationRatio,                  // 최대종목 / 개별주합 %
            String concentrationLevel,               // 낮음 / 보통 / 높음
            String dividendNote,                     // "거의 없음"
            BigDecimal potentialMonthlyDividend      // 배당형으로 옮기면 생기는 월 현금흐름(추정)
    ) {
    }
}
