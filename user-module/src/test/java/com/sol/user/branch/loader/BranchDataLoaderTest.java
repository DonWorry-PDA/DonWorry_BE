package com.sol.user.branch.loader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV 1라인 파싱 계약을 고정한다. 영업점 원본 데이터는 주소에 콤마가 섞일 수 있어,
 * 따옴표 보존·BOM 제거가 깨지면 적재 시 컬럼이 밀려 위경도가 어긋난다.
 */
class BranchDataLoaderTest {

    @Test
    void 일반_라인은_콤마로_분할된다() {
        String[] cols = BranchDataLoader.parseCsvLine("남대문,서울특별시 중구 남대문로,02-123-4567,37.5,126.9");

        assertThat(cols).containsExactly("남대문", "서울특별시 중구 남대문로", "02-123-4567", "37.5", "126.9");
    }

    @Test
    void 따옴표로_감싼_필드_안의_콤마는_보존된다() {
        String[] cols = BranchDataLoader.parseCsvLine("지점,\"서울시 중구, 1층\",02-000,37.5,126.9");

        assertThat(cols).containsExactly("지점", "서울시 중구, 1층", "02-000", "37.5", "126.9");
    }

    @Test
    void 이스케이프된_따옴표는_한_개로_복원된다() {
        String[] cols = BranchDataLoader.parseCsvLine("\"가\"\"나\",주소,전화");

        assertThat(cols).containsExactly("가\"나", "주소", "전화");
    }

    @Test
    void 빈_필드도_위치를_유지한다() {
        String[] cols = BranchDataLoader.parseCsvLine("이름,,전화");

        assertThat(cols).containsExactly("이름", "", "전화");
    }

    @Test
    void BOM이_붙은_첫_줄은_제거된다() {
        String withBom = "﻿지점명";

        assertThat(BranchDataLoader.stripBom(withBom)).isEqualTo("지점명");
    }

    @Test
    void BOM이_없으면_원문_그대로다() {
        assertThat(BranchDataLoader.stripBom("지점명")).isEqualTo("지점명");
    }
}
