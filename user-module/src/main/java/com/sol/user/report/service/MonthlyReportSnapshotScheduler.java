package com.sol.user.report.service;

import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * 월말 자산 스냅샷 저장 스케줄러.
 * 매월 말일 23:00에 전체 사용자의 총자산을 {@link MonthlyReport}에 기록한다.
 * 다음 달 월간 리포트에서 "자산 변화(전월비)" 계산의 기준값으로 사용된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportSnapshotScheduler {

    private final UserRepository userRepository;
    private final MonthlyReportSnapshotPersister persister;

    @Scheduled(cron = "0 0 23 L * *", zone = "Asia/Seoul")
    public void saveMonthlySnapshot() {
        String month = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
        List<User> users = userRepository.findAllByPasswordIsNotNullOrderByUserIdAsc();
        log.info("월간 자산 스냅샷 저장 시작 month={} 대상사용자={}", month, users.size());

        int saved = 0;
        for (User user : users) {
            try {
                persister.saveForUser(user, month);
                saved++;
            } catch (Exception e) {
                log.error("월간 자산 스냅샷 저장 실패 userId={}: {}", user.getUserId(), e.getMessage(), e);
            }
        }
        log.info("월간 자산 스냅샷 저장 완료 month={} 저장={}/{}", month, saved, users.size());
    }
}
