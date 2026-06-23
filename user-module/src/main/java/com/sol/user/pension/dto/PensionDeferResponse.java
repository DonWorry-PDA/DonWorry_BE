package com.sol.user.pension.dto;

import lombok.Builder;
import java.util.List;

@Builder
public record PensionDeferResponse(
    PensionDeferDetail selected,
    List<PensionDeferComparisonRow> comparisonTable
) {}
