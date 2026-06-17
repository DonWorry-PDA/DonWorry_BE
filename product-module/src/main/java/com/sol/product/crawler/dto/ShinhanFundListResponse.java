package com.sol.product.crawler.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShinhanFundListResponse {

    private Integer totalCount;
    private Integer toalPage;
    private Integer nowPage;
    private List<ShinhanFundItem> items;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShinhanFundItem {
        private String FUND_CD;
        private String FUND_NM;
        private String FUND_TYPE_NEW;
        private String FUND_DOMESTIC_YN;
        private String ESTAB_DT;

        private BigDecimal TOT_ASSET;
        private BigDecimal NAV;
        private BigDecimal FUND_PRI;

        private BigDecimal PRE_CHARGE;
        private String REP_CHARGE;
        private BigDecimal MNG_FEE;
        private BigDecimal SALE_FEE;
        private BigDecimal ETC_FEE;

        private BigDecimal M1_RTN;
        private BigDecimal M3_RTN;
        private BigDecimal M6_RTN;
        private BigDecimal M12_RTN;
        private BigDecimal M36_RTN;
        private BigDecimal M60_RTN;

        private String WORK_DT;
        private String CONTENT;
        private String AMAK_14;
    }
}