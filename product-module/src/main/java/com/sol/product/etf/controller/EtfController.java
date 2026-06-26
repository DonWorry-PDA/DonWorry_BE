package com.sol.product.etf.controller;

import com.sol.common.response.ApiResponse;
import com.sol.product.etf.dto.EtfDocumentResponse;
import com.sol.product.etf.dto.EtfMonthlyDividendItem;
import com.sol.product.etf.dto.EtfPoolItem;
import com.sol.product.etf.dto.EtfResponse;
import com.sol.product.etf.realtime.EtfRealtimeResponse;
import com.sol.product.etf.realtime.EtfRealtimeService;
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
    private final EtfRealtimeService etfRealtimeService;

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

    @Operation(summary = "포트폴리오 추천용 ETF 풀 조회 (ticker 벌크)")
    @GetMapping("/pool")
    public ResponseEntity<ApiResponse<List<EtfPoolItem>>> getPool(@RequestParam List<String> tickers) {
        return ResponseEntity.ok(ApiResponse.ok(etfService.getPoolByTickers(tickers)));
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

    @Operation(summary = "ETF 월 분배금 일괄 조회 (product_id 목록)")
    @GetMapping("/monthly-dividends")
    public ResponseEntity<ApiResponse<List<EtfMonthlyDividendItem>>> getMonthlyDividends(
            @RequestParam List<Long> productIds) {
        return ResponseEntity.ok(ApiResponse.ok(etfService.getMonthlyDividends(productIds)));
    }

    @Operation(summary = "ETF 문서 URL 조회 (투자설명서·간이투자설명서·집합투자규약)")
    @GetMapping("/ticker/{tickerCode}/documents")
    public ResponseEntity<ApiResponse<EtfDocumentResponse>> getEtfDocuments(@PathVariable String tickerCode) {
        return ResponseEntity.ok(ApiResponse.ok(etfService.getEtfDocuments(tickerCode)));
    }

    @Operation(summary = "ETF 실시간 시세 전체 조회 (화이트리스트 26종)")
    @GetMapping("/realtime")
    public ResponseEntity<ApiResponse<List<EtfRealtimeResponse>>> getAllRealtime() {
        return ResponseEntity.ok(ApiResponse.ok(etfRealtimeService.getAllRealtime()));
    }

    @Operation(summary = "ETF 실시간 시세 단건 조회")
    @GetMapping("/realtime/{ticker}")
    public ResponseEntity<ApiResponse<EtfRealtimeResponse>> getRealtime(@PathVariable String ticker) {
        return ResponseEntity.ok(ApiResponse.ok(etfRealtimeService.getRealtime(ticker)));
    }

    @Operation(summary = "ETF 현재가 단건 조회 (Redis → 일봉 종가 fallback)")
    @GetMapping("/{productId}/price")
    public ResponseEntity<ApiResponse<Long>> getCurrentPrice(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(etfRealtimeService.getCurrentPriceByProductId(productId)));
    }
}
