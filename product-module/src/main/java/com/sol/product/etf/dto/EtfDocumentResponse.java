package com.sol.product.etf.dto;

import com.sol.product.etf.entity.EtfDetail;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EtfDocumentResponse {

    private String tickerCode;
    private String prospectusUrl;
    private String simplifiedUrl;
    private String fundRulesUrl;

    public static EtfDocumentResponse from(EtfDetail etfDetail) {
        return EtfDocumentResponse.builder()
                .tickerCode(etfDetail.getTickerCode())
                .prospectusUrl(etfDetail.getProspectusUrl())
                .simplifiedUrl(etfDetail.getSimplifiedUrl())
                .fundRulesUrl(etfDetail.getFundRulesUrl())
                .build();
    }
}
