package com.sol.user.report.service;

import com.sol.user.asset.service.AssetAggregator;
import com.sol.user.report.entity.MonthlyReport;
import com.sol.user.report.repository.MonthlyReportRepository;
import com.sol.user.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MonthlyReportSnapshotPersister {

    private final AssetAggregator assetAggregator;
    private final MonthlyReportRepository reportRepository;

    @Transactional
    public void saveForUser(User user, String month) {
        BigDecimal totalAsset = assetAggregator.aggregate(user.getUserId()).grossTotal();
        reportRepository.findByUserUserIdAndCurrentMonth(user.getUserId(), month)
                .ifPresentOrElse(
                        existing -> existing.updateTotalAsset(totalAsset),
                        () -> reportRepository.save(MonthlyReport.snapshot(user, month, totalAsset))
                );
    }
}
