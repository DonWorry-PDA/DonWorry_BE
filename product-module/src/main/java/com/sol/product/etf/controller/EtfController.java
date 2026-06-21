package com.sol.product.etf.controller;

import com.sol.common.response.ApiResponse;
import com.sol.product.etf.dto.EtfResponse;
import com.sol.product.etf.service.EtfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ETF", description = "ETF 상품 조회 API")
@RestController
@RequestMapping("/api/product/etfs")
@RequiredArgsConstructor
public class EtfController {

    private final EtfService etfService;

    @Operation(summary = "ETF 전체 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EtfResponse>>> getAllEtfs() {
        return ResponseEntity.ok(ApiResponse.ok(etfService.getAllEtfs()));
    }

    @Operation(summary = "운용사별 ETF 조회")
    @GetMapping("/asset-manager/{assetManager}")
    public ResponseEntity<ApiResponse<List<EtfResponse>>> getEtfsByAssetManager(@PathVariable String assetManager) {
        return ResponseEntity.ok(ApiResponse.ok(etfService.getEtfsByAssetManager(assetManager)));
    }

    @Operation(summary = "종목코드로 ETF 단건 조회")
    @GetMapping("/ticker/{tickerCode}")
    public ResponseEntity<ApiResponse<EtfResponse>> getEtfByTickerCode(@PathVariable String tickerCode) {
        return ResponseEntity.ok(ApiResponse.ok(etfService.getEtfByTickerCode(tickerCode)));
    }

    @Operation(summary = "상품 ID로 ETF 단건 조회")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<EtfResponse>> getEtfByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(etfService.getEtfByProductId(productId)));
    }
}
