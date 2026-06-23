package com.sol.user.mydata.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.mydata.dto.MydataAccountResponse;
import com.sol.user.mydata.dto.MydataHoldingResponse;
import com.sol.user.mydata.dto.MydataPensionResponse;
import com.sol.user.mydata.dto.MydataTransactionsResponse;
import com.sol.user.mydata.service.MydataQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "MyData", description = "사용자 마이데이터 조회 API")
@RestController
@RequestMapping("/api/user/mydata")
@RequiredArgsConstructor
public class MydataController {

    private final MydataQueryService mydataQueryService;

    @Operation(summary = "연결 계좌 목록 조회")
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<MydataAccountResponse>>> getAccounts(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(mydataQueryService.getAccounts(userId)));
    }

    @Operation(summary = "기간별 현금흐름 및 증권 거래내역 조회")
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<MydataTransactionsResponse>> getTransactions(
            @RequestAttribute("userId") Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(mydataQueryService.getTransactions(userId, from, to)));
    }

    @Operation(summary = "보유자산 목록 조회")
    @GetMapping("/holdings")
    public ResponseEntity<ApiResponse<List<MydataHoldingResponse>>> getHoldings(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(mydataQueryService.getHoldings(userId)));
    }

    @Operation(summary = "연금 목록 조회")
    @GetMapping("/pensions")
    public ResponseEntity<ApiResponse<List<MydataPensionResponse>>> getPensions(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(mydataQueryService.getPensions(userId)));
    }
}
