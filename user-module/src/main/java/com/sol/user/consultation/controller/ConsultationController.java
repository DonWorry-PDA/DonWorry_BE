package com.sol.user.consultation.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.consultation.dto.ConsultationCreateRequest;
import com.sol.user.consultation.dto.ConsultationMemoUpdateRequest;
import com.sol.user.consultation.dto.ConsultationResponse;
import com.sol.user.consultation.dto.ConsultationScheduleUpdateRequest;
import com.sol.user.consultation.dto.ConsultationSummaryResponse;
import com.sol.user.consultation.service.ConsultationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "상담", description = "마이페이지 상담 예약/내역/요약/메모 API")
@RestController
@RequestMapping("/api/user/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    @Operation(summary = "본인 데모 상담 시드", description = "시연용: 로그인한 사용자에게 예약 1건 + 완료 2건(요약 포함)을 생성한다.")
    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> seed(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(consultationService.seed(userId)));
    }

    @Operation(summary = "상담 예약 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<ConsultationResponse>> create(
            @RequestAttribute("userId") Long userId,
            @RequestBody ConsultationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(consultationService.create(userId, request)));
    }

    @Operation(summary = "상담 내역 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getMyConsultations(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(consultationService.getMyConsultations(userId)));
    }

    @Operation(summary = "상담 상세")
    @GetMapping("/{consultationId}")
    public ResponseEntity<ApiResponse<ConsultationResponse>> getMyConsultation(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultationId) {
        return ResponseEntity.ok(ApiResponse.ok(consultationService.getMyConsultation(userId, consultationId)));
    }

    @Operation(summary = "상담 일정 변경", description = "예약 상태에서만 가능.")
    @PatchMapping("/{consultationId}/schedule")
    public ResponseEntity<ApiResponse<ConsultationResponse>> changeSchedule(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultationId,
            @RequestBody ConsultationScheduleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                consultationService.changeSchedule(userId, consultationId, request)));
    }

    @Operation(summary = "상담 예약 취소", description = "예약 상태에서만 가능.")
    @PostMapping("/{consultationId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultationId) {
        consultationService.cancel(userId, consultationId);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }

    @Operation(summary = "상담 요약 조회", description = "완료 상담의 PB 요약 + 내 메모.")
    @GetMapping("/{consultationId}/summary")
    public ResponseEntity<ApiResponse<ConsultationSummaryResponse>> getSummary(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultationId) {
        return ResponseEntity.ok(ApiResponse.ok(consultationService.getSummary(userId, consultationId)));
    }

    @Operation(summary = "내 메모 저장")
    @PatchMapping("/{consultationId}/memo")
    public ResponseEntity<ApiResponse<Void>> updateMemo(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long consultationId,
            @RequestBody ConsultationMemoUpdateRequest request) {
        consultationService.updateMemo(userId, consultationId, request);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }
}
