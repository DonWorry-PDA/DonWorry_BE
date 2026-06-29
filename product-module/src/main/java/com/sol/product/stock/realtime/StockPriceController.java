package com.sol.product.stock.realtime;

import com.sol.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "주식", description = "개별주식 시세 API")
@RestController
@RequestMapping("/api/product/stocks")
@RequiredArgsConstructor
public class StockPriceController {

    private final StockPriceService stockPriceService;

    @Operation(summary = "주식 현재가 배치 조회 (productId 목록 → 가격 맵)")
    @GetMapping("/prices")
    public ResponseEntity<ApiResponse<Map<Long, Long>>> getBatchPrices(
            @RequestParam List<Long> productIds) {
        return ResponseEntity.ok(ApiResponse.ok(stockPriceService.getBatchPrices(productIds)));
    }
}
