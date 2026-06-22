package com.sol.user.monthlysalary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class SalaryAssetExclusionRequest {
    @NotNull(message = "제외할 자산 목록은 필수입니다. 비우려면 빈 배열([])을 보내주세요.")
    @Size(max = 100, message = "제외할 자산은 최대 100개까지 선택할 수 있습니다.")
    private List<String> excludedAssetKeys;
}
