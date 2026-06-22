package com.sol.user.monthlysalary.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SalaryAssetListResponse {
    private List<AssetGroupDto> assetGroups;
}
