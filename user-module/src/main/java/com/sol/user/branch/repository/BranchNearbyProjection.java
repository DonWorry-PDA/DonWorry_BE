package com.sol.user.branch.repository;

/** 근처 영업점 네이티브 쿼리 결과 투영. distanceM은 ST_Distance_Sphere가 반환하는 미터 거리. */
public interface BranchNearbyProjection {
    Long getId();

    String getName();

    String getAddress();

    String getPhone();

    String getRegion();

    double getDistanceM();
}
