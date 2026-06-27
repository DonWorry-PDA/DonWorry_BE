package com.sol.user.mydata.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 연결 기관 도메인 카테고리(#211).
 *
 * <p>{@code AssetConnection.category}에 저장되는 값(BANK·SECURITIES·PENSION·INSURANCE·CARD·LOAN)과
 * 1:1로 대응한다. 선언 순서가 곧 우선순위 = 표시 정렬 순서다(은행·증권 우선 → 연금·보험·카드 → 대출).
 *
 * <p>한 기관이 여러 category로 연결돼 있으면({@code 신한은행 BANK+LOAN}) 우선순위가 가장 높은
 * (ordinal이 작은) 하나를 대표 category로 고른다. label·색은 카탈로그({@link InstitutionCode})에
 * 기관명 매칭이 없을 때 쓰는 폴백값이다.
 */
@Getter
@RequiredArgsConstructor
public enum InstitutionCategory {

    BANK("은행", "#0046FF", "#FFFFFF"),
    SECURITIES("증권", "#1F3A93", "#FFFFFF"),
    PENSION("연금", "#00A86B", "#FFFFFF"),
    INSURANCE("보험", "#5B6BC0", "#FFFFFF"),
    CARD("카드", "#E0457B", "#FFFFFF"),
    LOAN("대출", "#6B7280", "#FFFFFF");

    /** 카탈로그 매칭 실패 시 폴백 라벨 */
    private final String fallbackLabel;
    /** 카탈로그 매칭 실패 시 폴백 브랜드색 */
    private final String fallbackBrandColor;
    /** 카탈로그 매칭 실패 시 폴백 라벨색 */
    private final String fallbackLabelColor;

    /** 우선순위(작을수록 우선·앞 정렬). 선언 순서 기준. */
    public int priority() {
        return ordinal();
    }

    public static Optional<InstitutionCategory> from(String category) {
        return Arrays.stream(values())
                .filter(c -> c.name().equals(category))
                .findFirst();
    }
}
