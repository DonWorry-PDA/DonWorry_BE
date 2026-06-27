package com.sol.user.mydata.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum InstitutionCode {

    // 기존 은행
    SHINHAN_BANK("shinhan_bank", "신한은행", "bank", "신한", "#0046FF", "#FFFFFF",
            List.of("신한은행")),
    SHINHAN_INV("shinhan_inv", "신한투자증권", "securities", "신한", "#0046FF", "#FFFFFF",
            List.of("신한투자증권")),
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
    // 추가 은행
    IBK("ibk", "IBK기업은행", "bank", "IBK", "#005BAC", "#FFFFFF",
            List.of("IBK기업은행")),
    KDB("kdb", "KDB산업은행", "bank", "KDB", "#003580", "#FFFFFF",
            List.of("KDB산업은행")),
    SC("sc", "SC제일은행", "bank", "SC", "#1D4F91", "#FFFFFF",
            List.of("SC제일은행")),
    CITI("citi", "한국씨티은행", "bank", "씨티", "#003B70", "#FFFFFF",
            List.of("한국씨티은행")),
    KBANK("kbank", "케이뱅크", "bank", "케이", "#2C68C4", "#FFFFFF",
            List.of("케이뱅크")),
    POST("post", "우체국은행", "bank", "우체국", "#C8161D", "#FFFFFF",
            List.of("우체국은행")),
    MG("mg", "MG새마을금고", "bank", "MG", "#00703C", "#FFFFFF",
            List.of("MG새마을금고")),
    SUHYUP("suhyup", "수협은행", "bank", "수협", "#005BAC", "#FFFFFF",
            List.of("수협은행")),
    BNK("bnk", "BNK부산은행", "bank", "BNK", "#00388B", "#FFFFFF",
            List.of("BNK부산은행")),
    DAEGU("daegu", "iM뱅크", "bank", "iM", "#004B9B", "#FFFFFF",
            List.of("iM뱅크")),
    JB("jb", "전북은행", "bank", "JB", "#005BAC", "#FFFFFF",
            List.of("전북은행")),
    JEJU("jeju", "제주은행", "bank", "제주", "#004D99", "#FFFFFF",
            List.of("제주은행")),
    // 기존 증권사
    SAMSUNG("samsung", "삼성증권", "securities", "삼성", "#1428A0", "#FFFFFF",
            List.of("삼성증권")),
    MIRAE("mirae", "미래에셋증권", "securities", "미래", "#1F3A93", "#FFFFFF",
            List.of("미래에셋증권")),
    KOREA_INV("korea_inv", "한국투자증권", "securities", "한투", "#FF6B00", "#FFFFFF",
            List.of("한국투자증권")),
    KIWOOM("kiwoom", "키움증권", "securities", "키움", "#E30613", "#FFFFFF",
            List.of("키움증권")),
    NH_INV("nh_inv", "NH투자증권", "securities", "NH투자", "#00A651", "#FFFFFF",
            List.of("NH투자증권")),
    // 추가 증권사
    HANA_INV("hana_inv", "하나증권", "securities", "하나", "#009B88", "#FFFFFF",
            List.of("하나증권")),
    DAISHIN("daishin", "대신증권", "securities", "대신", "#003087", "#FFFFFF",
            List.of("대신증권")),
    MERITZ("meritz", "메리츠증권", "securities", "메리츠", "#E30613", "#FFFFFF",
            List.of("메리츠증권")),
    HANWHA("hanwha", "한화투자증권", "securities", "한화", "#FF6600", "#FFFFFF",
            List.of("한화투자증권")),
    KYOBO("kyobo", "교보증권", "securities", "교보", "#1B4B8F", "#FFFFFF",
            List.of("교보증권")),
    YUANTA("yuanta", "유안타증권", "securities", "유안타", "#FFB900", "#3A2E00",
            List.of("유안타증권")),
    DB("db", "DB금융투자", "securities", "DB", "#003780", "#FFFFFF",
            List.of("DB금융투자")),
    EBEST("ebest", "이베스트투자증권", "securities", "이베스트", "#00338D", "#FFFFFF",
            List.of("이베스트투자증권")),
    SHINYOUNG("shinyoung", "신영증권", "securities", "신영", "#003087", "#FFFFFF",
            List.of("신영증권")),
    EUGENE("eugene", "유진투자증권", "securities", "유진", "#00539B", "#FFFFFF",
            List.of("유진투자증권")),
    SK("sk", "SK증권", "securities", "SK", "#C8161D", "#FFFFFF",
            List.of("SK증권")),
    IM("im", "iM증권", "securities", "iM", "#00BEA2", "#FFFFFF",
            List.of("iM증권")),
    TOSS_INV("toss_inv", "토스증권", "securities", "토스", "#0064FF", "#FFFFFF",
            List.of("토스증권")),
    WOORI_INV("woori_inv", "우리투자증권", "securities", "우리투자", "#0067AC", "#FFFFFF",
            List.of("우리투자증권")),
    CAPE("cape", "케이프투자증권", "securities", "케이프", "#E30613", "#FFFFFF",
            List.of("케이프투자증권")),
    BOOKOOK("bookook", "부국증권", "securities", "부국", "#004B9B", "#FFFFFF",
            List.of("부국증권"));

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

    /** DB 기관명(dbNames)으로 카탈로그 코드를 찾는다. 연결 목록의 표시 메타 매칭에 사용(#211). */
    public static Optional<InstitutionCode> byDbName(String dbName) {
        return Arrays.stream(values())
                .filter(code -> code.dbNames.contains(dbName))
                .findFirst();
    }

    public static List<InstitutionCode> all() {
        return List.of(values());
    }
}
