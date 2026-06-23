package com.sol.user.asset.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * 자산관리 허브의 관리 메뉴 6개 미리보기 수치.
 * investmentCheck / pensionDefer / monthlyReport 의 수치는 후속 이슈(#2/#5/#6)에서 채워지며,
 * 그 전까지는 null 로 내려간다.
 */
@Builder
public record AssetHubMenus(
        SalaryMaking salaryMaking,
        LifeStability lifeStability,
        InvestmentCheck investmentCheck,
        PensionDefer pensionDefer,
        RetirementSim retirementSim,
        MonthlyReport monthlyReport
) {

    /** 월급 만들기: 목표 생활비 대비 현재 현금흐름 달성률. */
    @Builder
    public record SalaryMaking(Integer achievementRate, BigDecimal targetAmount, BigDecimal currentAmount) {
    }

    /** 생활 안정도: 최근 산출된 등급과 생활비 충당률. */
    @Builder
    public record LifeStability(String grade, String gradeLabel, Integer coverageRate) {
    }

    /** 투자 건강검진: 월급(현금흐름)을 만드는 자산 비중. (#2) */
    public record InvestmentCheck(Integer cashflowAssetRatio) {
    }

    /** 국민연금 연기: 추천 연기 연수와 평생 증가액. (#5) */
    @Builder
    public record PensionDefer(Integer deferYears, BigDecimal lifetimeIncrease) {
    }

    /** 은퇴 시뮬레이션: 진입 가능 여부. */
    public record RetirementSim(boolean available) {
    }

    /** 월간 리포트: 신규 도착 여부와 대상 월. (#6) */
    @Builder
    public record MonthlyReport(Boolean isNew, String month) {
    }
}
