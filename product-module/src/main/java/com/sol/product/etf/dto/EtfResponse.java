package com.sol.product.etf.dto;

import com.sol.product.etf.entity.EtfDetail;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class EtfResponse {

    private Long productId;
    private String productName;
    private String currency;
    private String tickerCode;
    private String benchmarkIndex;
    private String assetManager;
    private BigDecimal totalExpenseRatio;
    private LocalDate listingDate;
    private String availableAccountTypes;

    public static EtfResponse from(EtfDetail etfDetail) {
        return EtfResponse.builder()
                .productId(etfDetail.getProduct().getProductId())
                .productName(etfDetail.getProduct().getProductName())
                .currency(etfDetail.getProduct().getCurrency())
                .tickerCode(etfDetail.getTickerCode())
                .benchmarkIndex(etfDetail.getBenchmarkIndex())
                .assetManager(etfDetail.getAssetManager())
                .totalExpenseRatio(etfDetail.getTotalExpenseRatio())
                .listingDate(etfDetail.getListingDate())
                .availableAccountTypes(etfDetail.getAvailableAccountTypes())
                .build();
    }
}