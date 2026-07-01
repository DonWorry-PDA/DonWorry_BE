package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetIncomeResponse;
import com.sol.user.asset.dto.AssetIncomeResponse.IncomeSource;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.asset.mapper.AssetMapper;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.holding.service.EtfDividendCalculator;
import com.sol.user.holding.service.EtfDividendCalculator.DividendBreakdown;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssetIncomeService {

    private final PensionRepository pensionRepository;
    private final HoldingRepository holdingRepository;
    private final AccountRepository accountRepository;
    private final DepositDetailClient depositDetailClient;
    private final UserRepository userRepository;
    private final AssetMapper assetMapper;
    private final EtfDividendCalculator etfDividendCalculator;

    public AssetIncomeResponse getIncome(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<Account> accounts = accountRepository.findByUserUserId(userId);
        // 분배금은 단일 출처(EtfDividendCalculator)에서 연금/비연금 분해된 세전 값을 받는다(#303).
        DividendBreakdown dividend = etfDividendCalculator.monthlyDividendBreakdown(userId, Set.of());
        BigDecimal nationalPension = Boolean.TRUE.equals(user.getNationalPensionReceiving())
                ? calcNationalPension(userId).setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal etfDividend = dividend.nonPensionGross().setScale(0, RoundingMode.HALF_UP);
        BigDecimal depositInterest = calcDepositInterest(accounts).setScale(0, RoundingMode.HALF_UP);
        BigDecimal pensionDividend = dividend.pensionGross().setScale(0, RoundingMode.HALF_UP);
        BigDecimal unrealizedGainLoss = holdingRepository.sumUnrealizedGainLossByUserId(userId);

        BigDecimal accessibleIncome = nationalPension.add(etfDividend).add(depositInterest);
        BigDecimal lockedIncome = pensionDividend;
        BigDecimal totalMonthlyIncome = accessibleIncome.add(lockedIncome);

        List<IncomeSource> sources = assetMapper.toIncomeSources(
                nationalPension, etfDividend, depositInterest, pensionDividend);

        return AssetIncomeResponse.builder()
                .totalMonthlyIncome(totalMonthlyIncome)
                .accessibleIncome(accessibleIncome)
                .lockedIncome(lockedIncome)
                .totalUnrealizedGainLoss(unrealizedGainLoss == null ? BigDecimal.ZERO : unrealizedGainLoss)
                .sources(sources)
                .build();
    }

    // 국민연금: pensionType = "NATIONAL"
    private BigDecimal calcNationalPension(Long userId) {
        return pensionRepository.findMonthlyAmount(userId, "NATIONAL")
                .orElse(BigDecimal.ZERO);
    }

    // 예금 이자: depositBalance × interestRate / 1200
    private BigDecimal calcDepositInterest(List<Account> accounts) {
        List<Long> depositProductIds = accounts.stream()
                .filter(a -> "DEPOSIT".equals(a.getAccountType()) && a.getProductId() != null)
                .map(Account::getProductId)
                .toList();

        if (depositProductIds.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<Long, DepositDetailItem> details = depositDetailClient.fetchDepositDetails(depositProductIds);

        return accounts.stream()
                .filter(a -> "DEPOSIT".equals(a.getAccountType()) && a.getProductId() != null)
                .filter(a -> details.containsKey(a.getProductId()))
                .map(a -> {
                    DepositDetailItem detail = details.get(a.getProductId());
                    BigDecimal balance = a.getDepositBalance() == null ? BigDecimal.ZERO : a.getDepositBalance();
                    BigDecimal rate = detail.interestRate() == null ? BigDecimal.ZERO : detail.interestRate();
                    // balance × rate / 1200
                    return balance.multiply(rate).divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
