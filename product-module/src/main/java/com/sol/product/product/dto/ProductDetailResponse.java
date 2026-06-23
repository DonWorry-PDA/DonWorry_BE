package com.sol.product.product.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductDetailResponse {

    private Long productId;
    private String productName;
    private String productType;

    private EtfDetailDto etfDetail;
    private DepositDetailDto depositDetail;
    private PensionSavingDetailDto pensionSavingDetail;
}
