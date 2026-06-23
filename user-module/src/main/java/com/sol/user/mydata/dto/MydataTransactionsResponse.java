package com.sol.user.mydata.dto;

import java.util.List;

public record MydataTransactionsResponse(
        List<MydataCashFlowResponse> cashFlows,
        List<MydataTradeResponse> trades
) {
}
