//package com.sol.product.crawler;
//
//import com.sol.product.crawler.dto.ShinhanFundListResponse;
//import com.sol.product.crawler.dto.ShinhanFundListResponse.ShinhanFundItem;
//import org.springframework.http.*;
//import org.springframework.stereotype.Component;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.ArrayList;
//import java.util.List;
//
//@Component
//public class ShinhanFundCrawler {
//
//    private static final String FUND_LIST_API =
//            "https://www.shinhanfund.com/api/fund/getList";
//
//    private final RestTemplate restTemplate = new RestTemplate();
//
//    public void testCrawl() {
//        List<ShinhanFundItem> funds = crawlAllFunds();
//
//        System.out.println("전체 수집 개수 = " + funds.size());
//
//        for (int i = 0; i < Math.min(funds.size(), 20); i++) {
//            ShinhanFundItem item = funds.get(i);
//
//            System.out.println(
//                    (i + 1) + ". " +
//                            item.getFUND_CD() + " | " +
//                            item.getFUND_NM() + " | " +
//                            item.getFUND_TYPE_NEW() + " | " +
//                            toDomesticType(item.getFUND_DOMESTIC_YN()) + " | " +
//                            toRiskGrade(item.getAMAK_14()) + " | " +
//                            "기준가=" + item.getFUND_PRI() + " | " +
//                            "1년=" + item.getM12_RTN()
//            );
//        }
//    }
//
//    public List<ShinhanFundItem> crawlAllFunds() {
//        List<ShinhanFundItem> result = new ArrayList<>();
//
//        ShinhanFundListResponse firstResponse = requestPage(1);
//
//        if (firstResponse == null || firstResponse.getItems() == null) {
//            throw new IllegalStateException("신한자산운용 펀드 목록 조회 실패");
//        }
//
//        int totalPage = firstResponse.getToalPage() == null ? 1 : firstResponse.getToalPage();
//
//        result.addAll(firstResponse.getItems());
//
//        System.out.println("totalCount = " + firstResponse.getTotalCount());
//        System.out.println("totalPage = " + totalPage);
//        System.out.println("page 1 수집 완료: " + firstResponse.getItems().size() + "건");
//
//        for (int page = 2; page <= totalPage; page++) {
//            ShinhanFundListResponse response = requestPage(page);
//
//            if (response == null || response.getItems() == null) {
//                System.out.println("page " + page + " 수집 실패");
//                continue;
//            }
//
//            result.addAll(response.getItems());
//
//            System.out.println("page " + page + " 수집 완료: " + response.getItems().size() + "건");
//
//            sleep();
//        }
//
//        return result;
//    }
//
//    private ShinhanFundListResponse requestPage(int page) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//        headers.set("X-Requested-With", "XMLHttpRequest");
//        headers.set("User-Agent", "Mozilla/5.0");
//        headers.set("Origin", "https://www.shinhanfund.com");
//        headers.set("Referer", "https://www.shinhanfund.com/ko/pc/fund/");
//
//        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
//
//        body.add("page", String.valueOf(page));
//        body.add("orderBy", "recommend_view");
//
//        HttpEntity<MultiValueMap<String, String>> request =
//                new HttpEntity<>(body, headers);
//
//        ResponseEntity<ShinhanFundListResponse> response =
//                restTemplate.postForEntity(FUND_LIST_API, request, ShinhanFundListResponse.class);
//
//        return response.getBody();
//    }
//
//    private String toDomesticType(String value) {
//        if ("Y".equals(value)) {
//            return "국내";
//        }
//        if ("N".equals(value)) {
//            return "해외";
//        }
//        return "미분류";
//    }
//
//    private String toRiskGrade(String code) {
//        if (code == null) {
//            return "미분류";
//        }
//
//        return switch (code) {
//            case "1" -> "매우 높은 위험";
//            case "2" -> "높은 위험";
//            case "3" -> "다소 높은 위험";
//            case "4" -> "보통 위험";
//            case "5" -> "낮은 위험";
//            case "6" -> "매우 낮은 위험";
//            default -> "미분류";
//        };
//    }
//
//    private void sleep() {
//        try {
//            Thread.sleep(350);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }
//}