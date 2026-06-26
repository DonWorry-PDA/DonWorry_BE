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
}
