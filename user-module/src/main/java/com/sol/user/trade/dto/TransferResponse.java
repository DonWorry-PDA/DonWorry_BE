package com.sol.user.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        BigDecimal brokerageBalance,
        LocalDateTime transferredAt
) {}
