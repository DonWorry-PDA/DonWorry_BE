package com.sol.user.trade.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.trade.dto.BuyRequest;
import com.sol.user.trade.dto.BuyResponse;
import com.sol.user.trade.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trade", description = "매수 API")
@RestController
@RequestMapping("/api/user/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @Operation(summary = "ETF 매수", description = "지정한 ETF를 현재가로 매수합니다. BROKERAGE 계좌 잔고에서 차감됩니다.")
    @PostMapping("/buy")
    public ResponseEntity<ApiResponse<BuyResponse>> buy(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody BuyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tradeService.buy(userId, request)));
    }
}
