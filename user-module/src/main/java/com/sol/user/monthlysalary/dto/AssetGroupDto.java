package com.sol.user.monthlysalary.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AssetGroupDto {
    private String category;
    private String categoryLabel;
    private List<AssetItemDto> items;
}
