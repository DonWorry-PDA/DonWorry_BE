package com.sol.product.external.ls.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LsWsStockResponse(Header header, Body body) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String tr_cd, String tr_key) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            String shcode,   // 단축코드
            String price,    // 현재가
            String change,   // 전일대비
            String drate,    // 등락율
            String sign      // 전일대비구분 (2=상승, 3=보합, 5=하락)
    ) {}
}
