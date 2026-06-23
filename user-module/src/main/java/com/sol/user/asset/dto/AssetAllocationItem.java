package com.sol.user.asset.dto;

/**
 * 자산 분포 한 항목. ratio 는 정수 % (전체 합 100 보정).
 */
public record AssetAllocationItem(String category, int ratio) {
}
