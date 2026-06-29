package com.sol.user.branch.repository;

import com.sol.user.branch.entity.Branch;
import com.sol.user.branch.type.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    long countByInstitution(Institution institution);

    /**
     * 지정 기관의 영업점을, 주어진 좌표로부터 가까운 순으로 조회한다.
     * ST_Distance_Sphere는 미터 단위 구면 거리를 반환하며, POINT 인자는 (경도, 위도) 순서다.
     * 영업점 수백 개 규모라 인덱스 없는 풀스캔으로 충분하다.
     */
    @Query(value = """
            SELECT b.branch_id AS id,
                   b.name AS name,
                   b.address AS address,
                   b.phone AS phone,
                   b.region AS region,
                   ST_Distance_Sphere(POINT(b.longitude, b.latitude), POINT(:lng, :lat)) AS distanceM
            FROM branch b
            WHERE b.institution = :institution
            ORDER BY distanceM
            LIMIT :limit
            """, nativeQuery = true)
    List<BranchNearbyProjection> findNearby(@Param("lat") double lat,
                                            @Param("lng") double lng,
                                            @Param("institution") String institution,
                                            @Param("limit") int limit);
}
