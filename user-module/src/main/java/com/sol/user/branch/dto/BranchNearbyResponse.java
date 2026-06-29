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
        double distanceM = p.getDistanceM();
        return BranchNearbyResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .address(p.getAddress())
                .phone(p.getPhone())
                .region(p.getRegion())
                .distanceMeters(Math.round(distanceM))
                // 소수점 첫째 자리 km. 원본 거리 기준으로 환산해 미터 반올림과의 이중 반올림을 피한다.
                // 표시 포맷(800m / 1.2km)은 FE에서 처리.
                .distanceKm(Math.round(distanceM / 100.0) / 10.0)
                .build();
    }
}
