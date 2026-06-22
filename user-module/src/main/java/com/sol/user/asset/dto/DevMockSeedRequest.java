package com.sol.user.asset.dto;

import com.sol.user.asset.type.MockType;

/**
 * 개발/시연용 목업 시드 요청.
 * 특정 사용자에게 특정 생활 안정도 구간 시나리오를 강제로 배정한다.
 */
public record DevMockSeedRequest(Long userId, MockType scenario) {
}
