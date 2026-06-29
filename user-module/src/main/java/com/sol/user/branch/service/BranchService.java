package com.sol.user.branch.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.branch.dto.BranchNearbyResponse;
import com.sol.user.branch.repository.BranchRepository;
import com.sol.user.branch.type.Institution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final BranchRepository branchRepository;

    /** 사용자 좌표 기준으로 해당 기관의 가까운 영업점을 거리 오름차순으로 반환한다. */
    public List<BranchNearbyResponse> findNearby(double lat, double lng, Institution institution, Integer limit) {
        if (institution == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        validateCoordinate(lat, lng);
        int resolvedLimit = resolveLimit(limit);

        return branchRepository.findNearby(lat, lng, institution.name(), resolvedLimit).stream()
                .map(BranchNearbyResponse::from)
                .toList();
    }

    private void validateCoordinate(double lat, double lng) {
        // NaN은 모든 비교가 false라 범위 검사를 통과하므로 isFinite로 먼저 걸러낸다.
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
