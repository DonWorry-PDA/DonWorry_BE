package com.sol.user.branch.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.branch.dto.BranchNearbyResponse;
import com.sol.user.branch.service.BranchService;
import com.sol.user.branch.type.Institution;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "영업점", description = "위치 기반 근처 영업점 조회 API")
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @Operation(summary = "근처 영업점 조회",
            description = "현재 위경도와 기관(institution)으로 가까운 영업점을 거리 오름차순으로 반환한다.")
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<BranchNearbyResponse>>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam Institution institution,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(branchService.findNearby(lat, lng, institution, limit)));
    }
}
