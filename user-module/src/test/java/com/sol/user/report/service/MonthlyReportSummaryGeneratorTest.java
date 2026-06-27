package com.sol.user.report.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyReportSummaryGeneratorTest {

    private final MonthlyReportSummaryGenerator generator = new MonthlyReportSummaryGenerator();

    @Test
    void 항상_3개의_문장을_반환한다() {
        List<String> lines = generator.generate(BigDecimal.valueOf(100_000), BigDecimal.valueOf(12.4), 75, true);
        assertThat(lines).hasSize(3);
    }

    // ─── 배당 라인 ──────────────────────────────────────────────

    @Test
    void 배당증가이면_늘었어요_문구() {
        List<String> lines = generator.generate(BigDecimal.valueOf(100_000), BigDecimal.valueOf(12.4), 75, true);
        assertThat(lines.get(0)).contains("12.4%").contains("늘었어요");
    }

    @Test
    void 배당감소이면_줄었어요_문구() {
        List<String> lines = generator.generate(BigDecimal.valueOf(80_000), BigDecimal.valueOf(-5.0), 75, true);
        assertThat(lines.get(0)).contains("5%").contains("줄었어요");
    }

    @Test
    void 배당_전월비null이면_꾸준히_문구() {
        List<String> lines = generator.generate(BigDecimal.valueOf(50_000), null, 75, true);
        assertThat(lines.get(0)).contains("꾸준히");
    }

    @Test
    void 배당_전월비_0이면_동일_문구() {
        List<String> lines = generator.generate(BigDecimal.valueOf(50_000), BigDecimal.ZERO, 75, true);
        assertThat(lines.get(0)).contains("동일");
    }

    @Test
    void 배당금액_0이면_ETF투자_권유_문구() {
        List<String> lines = generator.generate(BigDecimal.ZERO, null, 75, true);
        assertThat(lines.get(0)).contains("아직 배당 수입이 없어요");
    }

    @Test
    void 배당금액_null이면_ETF투자_권유_문구() {
        List<String> lines = generator.generate(null, null, 75, true);
        assertThat(lines.get(0)).contains("아직 배당 수입이 없어요");
    }

    // ─── 잔액 라인 ──────────────────────────────────────────────

    @Test
    void 잔액충분이면_부족없음_문구() {
        List<String> lines = generator.generate(BigDecimal.valueOf(100_000), null, 75, true);
        assertThat(lines.get(1)).contains("잔액 부족 없이");
    }

    @Test
    void 잔액부족이면_확인필요_문구() {
        List<String> lines = generator.generate(BigDecimal.valueOf(100_000), null, 75, false);
        assertThat(lines.get(1)).contains("잔액 확인이 필요해요");
    }

    // ─── 소비 라인 ──────────────────────────────────────────────

    @Test
    void 소비비율_80이하_적정_문구() {
        List<String> lines = generator.generate(BigDecimal.valueOf(100_000), null, 80, true);
        assertThat(lines.get(2)).contains("잘 관리되고 있어요");
    }

    @Test
    void 소비비율_81이상_100이하_주의_문구() {
        List<String> lines = generator.generate(BigDecimal.valueOf(100_000), null, 95, true);
        assertThat(lines.get(2)).contains("지출 점검이 필요해요");
    }

    @Test
    void 소비비율_100초과_과다_문구() {
        List<String> lines = generator.generate(BigDecimal.valueOf(100_000), null, 110, true);
        assertThat(lines.get(2)).contains("수입을 초과했어요");
    }

    @Test
    void 배당_증가율_소수점1자리_표시() {
        // 12.45 → setScale(1, HALF_UP) → 12.5%
        List<String> lines = generator.generate(BigDecimal.valueOf(100_000), new BigDecimal("12.45"), 75, true);
        assertThat(lines.get(0)).contains("12.5%");
    }

    @Test
    void 배당_증가율_정수이면_소수점_생략() {
        // 10.0 → stripTrailingZeros → 10%
        List<String> lines = generator.generate(BigDecimal.valueOf(100_000), new BigDecimal("10.0"), 75, true);
        assertThat(lines.get(0)).contains("10%").doesNotContain("10.0%");
    }
}
