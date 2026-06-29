package com.sol.user.branch.dto;

import com.sol.user.branch.repository.BranchNearbyProjection;
import lombok.Builder;

@Builder
public record BranchNearbyResponse(
        Long id,
        String name,
        String address,
        String phone,
        String region,
        long distanceMeters,
        double distanceKm
) {
    public static BranchNearbyResponse from(BranchNearbyProjection p) {
        long meters = Math.round(p.getDistanceM());
        return BranchNearbyResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .address(p.getAddress())
                .phone(p.getPhone())
                .region(p.getRegion())
                .distanceMeters(meters)
                // 소수점 첫째 자리 km. 표시 포맷(800m / 1.2km)은 FE에서 처리.
                .distanceKm(Math.round(meters / 100.0) / 10.0)
                .build();
    }
}
