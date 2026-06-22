package com.sol.user.monthlysalary.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AssetItemDto {
    private String assetKey;
    private String name;
    private String description;
    private BigDecimal amount;
    private boolean excluded;

}
