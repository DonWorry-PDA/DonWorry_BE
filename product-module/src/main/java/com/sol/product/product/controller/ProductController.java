package com.sol.product.product.controller;

import com.sol.common.response.ApiResponse;
import com.sol.product.product.dto.ProductBatchItem;
import com.sol.product.product.dto.ProductDetailResponse;
import com.sol.product.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Product", description = "금융상품 공통 API")
@RestController
@RequestMapping("/api/product/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 상세 조회", description = "productType에 따라 etfDetail / depositDetail / pensionSavingDetail 중 하나만 non-null로 내려옵니다.")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductDetail(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductDetail(productId)));
    }

    @Operation(summary = "상품 일괄 조회 (product_id 목록)")
    @GetMapping("/batch")
    public ResponseEntity<ApiResponse<List<ProductBatchItem>>> getProductsByIds(
            @RequestParam List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductsByIds(ids)));
    }
}
