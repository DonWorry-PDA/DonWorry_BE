package com.sol.user.portfolio.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioConstantsTest {

    @Test
    void 사적연금_연금소득세율은_연령구간_경계에서_5_5_4_4_3_3으로_바뀐다() {
        assertThat(PortfolioConstants.privatePensionTaxRate(55)).isEqualByComparingTo("0.055");
        assertThat(PortfolioConstants.privatePensionTaxRate(69)).isEqualByComparingTo("0.055");
        assertThat(PortfolioConstants.privatePensionTaxRate(70)).isEqualByComparingTo("0.044");
        assertThat(PortfolioConstants.privatePensionTaxRate(79)).isEqualByComparingTo("0.044");
        assertThat(PortfolioConstants.privatePensionTaxRate(80)).isEqualByComparingTo("0.033");
    }

    @Test
    void 자본차익_비과세는_국내주식형_5종뿐이고_나머지는_과세다() {
        // 비과세 = 국내주식형 5종 (조사 전수검증)
        assertThat(PortfolioConstants.isCapitalGainExempt("411540")).isTrue(); // 200Top10
        assertThat(PortfolioConstants.isCapitalGainExempt("292500")).isTrue(); // KRX300
        assertThat(PortfolioConstants.isCapitalGainExempt("484880")).isTrue(); // 금융지주플러스고배당
        assertThat(PortfolioConstants.isCapitalGainExempt("0152E0")).isTrue(); // 배당성향탑픽
        assertThat(PortfolioConstants.isCapitalGainExempt("0105E0")).isTrue(); // 코리아고배당

        // 과세 = 해외주식형
        assertThat(PortfolioConstants.isCapitalGainExempt("433330")).isFalse(); // 미국S&P500
        assertThat(PortfolioConstants.isCapitalGainExempt("446720")).isFalse(); // 미국배당다우존스
        // 과세 = 채권혼합형 함정(코스피200 50% 담아도 혼합형이라 과세)
        assertThat(PortfolioConstants.isCapitalGainExempt("0192S0")).isFalse(); // 코스피200채권혼합50
        // 과세 = 채권형
        assertThat(PortfolioConstants.isCapitalGainExempt("438560")).isFalse(); // 국고채3년
    }
}
