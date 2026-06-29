package com.sol.user.branch.loader;

import com.sol.user.branch.entity.Branch;
import com.sol.user.branch.repository.BranchRepository;
import com.sol.user.branch.type.Institution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 정적 참조 데이터인 영업점을 앱 시작 시 1회 적재한다.
 * 해당 기관 데이터가 이미 있으면 건너뛰어 멱등하게 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BranchDataLoader implements ApplicationRunner {

    private static final String BANK_CSV = "branch/shinhan_bank_branches.csv";

    private final BranchRepository branchRepository;

    @Override
    public void run(ApplicationArguments args) {
        loadBank();
    }

    /** 신한은행: CSV에 위경도가 있어 그대로 적재 (지점명, 주소, 전화번호, 위도, 경도). */
    private void loadBank() {
        if (branchRepository.countByInstitution(Institution.SHINHAN_BANK) > 0) {
            return;
        }
        List<Branch> branches = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(BANK_CSV);
        try (InputStream in = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line = reader.readLine(); // 헤더 스킵
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = parseCsvLine(stripBom(line));
                if (cols.length < 5) {
                    log.warn("[BranchDataLoader] 컬럼 부족으로 건너뜀: {}", line);
                    continue;
                }
                branches.add(Branch.builder()
                        .institution(Institution.SHINHAN_BANK)
                        .name(cols[0].trim())
                        .address(cols[1].trim())
                        .phone(cols[2].trim())
                        .latitude(Double.parseDouble(cols[3].trim()))
                        .longitude(Double.parseDouble(cols[4].trim()))
                        .build());
            }
            branchRepository.saveAll(branches);
            log.info("[BranchDataLoader] 신한은행 영업점 {}건 적재 완료", branches.size());
        } catch (Exception e) {
            // 영업점은 조회 API의 전제 데이터라, 적재 실패를 삼키면 빈 결과만 반환된다. 부팅을 실패시킨다.
            log.error("[BranchDataLoader] 신한은행 영업점 적재 실패", e);
            throw new IllegalStateException("신한은행 영업점 초기 적재에 실패했습니다.", e);
        }
    }

    /** UTF-8 BOM 제거(파일 첫 줄에 붙는 경우 대비). */
    static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    /** 따옴표로 감싼 필드 안의 콤마를 보존하는 최소 CSV 파서(개행 미포함 1라인 기준). */
    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); // 이스케이프된 따옴표
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }
}
