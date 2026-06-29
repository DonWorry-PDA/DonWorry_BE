package com.sol.user.asset.dto;

import com.sol.user.asset.type.MockType;
import com.sol.user.portfolio.type.InvestmentPropensity;

/**
 * 개발/시연용 목업 시드 요청.
 * 특정 사용자에게 특정 자산 시나리오를 강제로 배정한다.
 * {@code propensity}는 투자성향(KYC, 5단계)을 자산 시나리오와 독립적으로 지정한다 — 생략(null)하면
 * 시나리오 기본 성향을 쓴다. 같은 자산에 성향만 바꿔 운용등급 차등을 시연하는 데 쓴다.
 */
public record DevMockSeedRequest(Long userId, MockType scenario, InvestmentPropensity propensity) {
}
