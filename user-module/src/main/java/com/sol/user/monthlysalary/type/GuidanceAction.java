package com.sol.user.monthlysalary.type;

/** 재진입 시 제시하는 선택지 — 각자 FE 라우트와 표시 라벨을 안다. */
public enum GuidanceAction {
    INCREASE_LIVING_COST("/mypage/profile-edit", "목표 생활비 올리기"),
    RETAKE_SURVEY("/survey", "다시 설문하고 재설계");

    private final String route;
    private final String label;

    GuidanceAction(String route, String label) {
        this.route = route;
        this.label = label;
    }

    public String getRoute() {
        return route;
    }

    public String getLabel() {
        return label;
    }
}
