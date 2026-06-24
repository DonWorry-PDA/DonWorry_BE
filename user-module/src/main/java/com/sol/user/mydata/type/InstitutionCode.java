package com.sol.user.mydata.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum InstitutionCode {

    SHINHAN("shinhan", "신한은행 · 신한투자증권", "bank", "신한", "#0046FF", "#FFFFFF",
            List.of("신한은행", "신한투자증권")),
    KB("kb", "KB국민은행", "bank", "KB", "#FFBC00", "#3A2E00",
            List.of("KB국민은행")),
    HANA("hana", "하나은행", "bank", "하나", "#009B88", "#FFFFFF",
            List.of("하나은행")),
    WOORI("woori", "우리은행", "bank", "우리", "#007AE6", "#FFFFFF",
            List.of("우리은행")),
    NH("nh", "NH농협은행", "bank", "농협", "#00A651", "#FFFFFF",
            List.of("NH농협은행")),
    KAKAO("kakao", "카카오뱅크", "bank", "카카오", "#FAE100", "#3C1E1E",
            List.of("카카오뱅크")),
    TOSS("toss", "토스뱅크", "bank", "토스", "#0064FF", "#FFFFFF",
            List.of("토스뱅크")),
    SAMSUNG("samsung", "삼성증권", "securities", "삼성", "#1428A0", "#FFFFFF",
            List.of("삼성증권")),
    MIRAE("mirae", "미래에셋증권", "securities", "미래", "#1F3A93", "#FFFFFF",
            List.of("미래에셋증권")),
    KOREA_INV("korea_inv", "한국투자증권", "securities", "한투", "#FF6B00", "#FFFFFF",
            List.of("한국투자증권")),
    KIWOOM("kiwoom", "키움증권", "securities", "키움", "#E30613", "#FFFFFF",
            List.of("키움증권")),
    NH_INV("nh_inv", "NH투자증권", "securities", "NH투자", "#00A651", "#FFFFFF",
            List.of("NH투자증권"));

    private final String id;
    private final String name;
    private final String type;
    private final String label;
    private final String brandColor;
    private final String labelColor;
    /** DB(account.institution_name, asset_connection.institution_name)에 저장된 기관명 목록 */
    private final List<String> dbNames;

    public static Optional<InstitutionCode> fromId(String id) {
        return Arrays.stream(values())
                .filter(code -> code.id.equals(id))
                .findFirst();
    }

    public static List<InstitutionCode> all() {
        return List.of(values());
    }
}
