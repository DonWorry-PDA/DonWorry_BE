package com.sol.user.mydata.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.mydata.dto.ConnectedInstitutionCountResponse;
import com.sol.user.mydata.dto.ConnectedInstitutionsResponse;
import com.sol.user.mydata.dto.InstitutionConnectRequest;
import com.sol.user.mydata.dto.InstitutionConnectResponse;
import com.sol.user.mydata.dto.InstitutionResponse;
import com.sol.user.mydata.dto.MydataAccountResponse;
import com.sol.user.mydata.dto.MydataHoldingResponse;
import com.sol.user.mydata.dto.MydataPensionResponse;
import com.sol.user.mydata.dto.MydataTransactionsResponse;
import com.sol.user.mydata.service.InstitutionService;
import com.sol.user.mydata.service.MydataQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final InstitutionService institutionService;

    @Operation(summary = "금융기관 목록 조회 (연결 가능 + 이미 연결된 기관)")
    @GetMapping("/institutions")
    public ResponseEntity<ApiResponse<List<InstitutionResponse>>> getInstitutions(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(institutionService.getInstitutions(userId)));
    }

    @Operation(summary = "연결된 기관 수 조회",
            description = "연결된 기관 수(기관명 distinct, 은행·증권·연금·보험·카드 전 도메인). 온보딩 응답과 동일한 값.")
    @GetMapping("/institutions/connected-count")
    public ResponseEntity<ApiResponse<ConnectedInstitutionCountResponse>> getConnectedInstitutionCount(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(institutionService.getConnectedInstitutionCount(userId)));
    }

    @Operation(summary = "연결된 기관 목록 조회",
            description = "연결된 기관 목록(AssetConnection 기준, 기관명 distinct, 전 도메인) + 카운트 동봉. "
                    + "한 기관이 여러 category면 1행으로 합치고 대표 category를 고른다. "
                    + "institutions.length == connectedInstitutionCount 보장(온보딩 카운트와 정합).")
    @GetMapping("/institutions/connected")
    public ResponseEntity<ApiResponse<ConnectedInstitutionsResponse>> getConnectedInstitutions(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(institutionService.getConnectedInstitutions(userId)));
    }

    @Operation(summary = "금융기관 연결")
    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<InstitutionConnectResponse>> connect(
            @RequestAttribute("userId") Long userId,
            @RequestBody @Valid InstitutionConnectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(institutionService.connect(userId, request.institutionIds())));
    }

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
