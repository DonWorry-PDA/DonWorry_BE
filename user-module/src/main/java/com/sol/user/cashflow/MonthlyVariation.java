package com.sol.user.cashflow;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * 페르소나 목업의 월별 변동을 <b>결정적</b>으로 만드는 헬퍼.
 *
 * <p>같은 {@link YearMonth}면 항상 같은 값을 돌려준다. 그래서 시드(자산 변화 백필)와
 * 리포트(읽기 경로)가 서로 다른 곳에서 계산해도 같은 달이면 같은 값이 나와 정합한다.
 *
 * <p>마이데이터 목업이 매달 똑같이 보이지 않도록(이자·배당·소비·시세) 흔드는 용도.
 * 거래별 노이즈는 합계에서 상쇄되므로, "월 단위 공통 계수"로 그 달 전체를 함께 흔든다.
 */
public final class MonthlyVariation {

    private MonthlyVariation() {
    }

    /** 현금흐름(이자·소비·관리비·다음달 고정비) 월별 계수. 0.90 ~ 1.10. */
    public static BigDecimal cashFactor(YearMonth ym) {
        return BigDecimal.valueOf(90 + (int) (hash(ym, 11L) % 21L), 2);
    }

    /** 배당 월별 계수. 분배는 더 들쭉날쭉해 0.80 ~ 1.20. */
    public static BigDecimal dividendFactor(YearMonth ym) {
        return BigDecimal.valueOf(80 + (int) (hash(ym, 23L) % 41L), 2);
    }

    /**
     * 보유자산 월간 시세수익률. -1.5% ~ +2.0%(약한 우상향 편향).
     * 순현금흐름만으로는 자산이 단조 증가만 하므로, 자산이 "늘고 주는" 출처가 된다.
     */
    public static BigDecimal marketReturn(YearMonth ym) {
        return BigDecimal.valueOf((int) (hash(ym, 37L) % 36L) - 15L, 3);
    }

    /** YearMonth + salt → 비음수 해시. 연·월만 입력이라 유저와 무관하게 같은 달이면 동일. */
    private static long hash(YearMonth ym, long salt) {
        long key = ym.getYear() * 12L + ym.getMonthValue();
        long h = (key * 2654435761L) ^ (salt * 1099511628211L);
        return h & Long.MAX_VALUE;
    }
}
