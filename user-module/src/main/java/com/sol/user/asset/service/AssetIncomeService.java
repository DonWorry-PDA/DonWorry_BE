package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetIncomeResponse;
import com.sol.user.asset.dto.AssetIncomeResponse.IncomeSource;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.holding.dto.HoldingWithQuantityAndType;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetIncomeService {

    private static final Set<String> PENSION_ACCOUNT_TYPES = Set.of("IRP", "PENSION_SAVING");

    private final PensionRepository pensionRepository;
    private final HoldingRepository holdingRepository;
    private final AccountRepository accountRepository;
    private final ProductBatchClient productBatchClient;
    private final DepositDetailClient depositDetailClient;

    public AssetIncomeResponse getIncome(Long userId) {
        List<HoldingWithQuantityAndType> allHoldings =
                holdingRepository.findHoldingsWithQuantityAndTypeByUserId(userId);
        BigDecimal nationalPension = calcNationalPension(userId);
        BigDecimal etfDividend = calcEtfDividend(allHoldings);
        BigDecimal depositInterest = calcDepositInterest(userId);
        BigDecimal pensionDividend = calcPensionDividend(allHoldings);
        BigDecimal unrealizedGainLoss = holdingRepository.sumUnrealizedGainLossByUserId(userId);

        BigDecimal accessibleIncome = nationalPension
                .add(etfDividend)
                .add(depositInterest)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal lockedIncome = pensionDividend
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal totalMonthlyIncome = accessibleIncome.add(lockedIncome);

        List<IncomeSource> sources = buildSources(
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

    // 비연금 계좌 ETF 배당: ETF 월배당 × 보유수량 합
    private BigDecimal calcEtfDividend(List<HoldingWithQuantityAndType> holdings) {
        List<Long> nonPensionProductIds = holdings.stream()
                .filter(h -> !PENSION_ACCOUNT_TYPES.contains(h.getAccountType()))
                .map(HoldingWithQuantityAndType::getProductId)
                .toList();

        if (nonPensionProductIds.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<Long, BigDecimal> dividends = productBatchClient.fetchEtfMonthlyDividends(nonPensionProductIds);

        return holdings.stream()
                .filter(h -> !PENSION_ACCOUNT_TYPES.contains(h.getAccountType()))
                .filter(h -> dividends.containsKey(h.getProductId()))
                .map(h -> {
                    BigDecimal monthlyDiv = dividends.get(h.getProductId());
                    BigDecimal quantity = h.getQuantity() == null ? BigDecimal.ZERO : h.getQuantity();
                    return monthlyDiv.multiply(quantity);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 예금 이자: depositBalance × interestRate / 1200
    private BigDecimal calcDepositInterest(Long userId) {
        List<Account> accounts = accountRepository.findByUserUserId(userId);

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

    // 연금 계좌 ETF 배당: accountType IN (IRP, PENSION_SAVING)
    private BigDecimal calcPensionDividend(List<HoldingWithQuantityAndType> holdings) {
        List<Long> pensionProductIds = holdings.stream()
                .filter(h -> PENSION_ACCOUNT_TYPES.contains(h.getAccountType()))
                .map(HoldingWithQuantityAndType::getProductId)
                .toList();

        if (pensionProductIds.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<Long, BigDecimal> dividends = productBatchClient.fetchEtfMonthlyDividends(pensionProductIds);

        return holdings.stream()
                .filter(h -> PENSION_ACCOUNT_TYPES.contains(h.getAccountType()))
                .filter(h -> dividends.containsKey(h.getProductId()))
                .map(h -> {
                    BigDecimal monthlyDiv = dividends.get(h.getProductId());
                    BigDecimal quantity = h.getQuantity() == null ? BigDecimal.ZERO : h.getQuantity();
                    return monthlyDiv.multiply(quantity);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<IncomeSource> buildSources(
            BigDecimal nationalPension,
            BigDecimal etfDividend,
            BigDecimal depositInterest,
            BigDecimal pensionDividend) {

        List<IncomeSource> sources = new ArrayList<>();

        BigDecimal roundedNationalPension = nationalPension.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundedEtfDividend = etfDividend.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundedDepositInterest = depositInterest.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundedPensionDividend = pensionDividend.setScale(0, RoundingMode.HALF_UP);

        if (roundedNationalPension.signum() > 0) {
            sources.add(IncomeSource.builder()
                    .type("NATIONAL_PENSION")
                    .label("국민연금")
                    .amount(roundedNationalPension)
                    .locked(false)
                    .build());
        }
        if (roundedEtfDividend.signum() > 0) {
            sources.add(IncomeSource.builder()
                    .type("ETF_DIVIDEND")
                    .label("ETF 배당")
                    .amount(roundedEtfDividend)
                    .locked(false)
                    .build());
        }
        if (roundedDepositInterest.signum() > 0) {
            sources.add(IncomeSource.builder()
                    .type("DEPOSIT_INTEREST")
                    .label("예금 이자")
                    .amount(roundedDepositInterest)
                    .locked(false)
                    .build());
        }
        if (roundedPensionDividend.signum() > 0) {
            sources.add(IncomeSource.builder()
                    .type("PENSION_DIVIDEND")
                    .label("연금 계좌 ETF 배당")
                    .amount(roundedPensionDividend)
                    .locked(true)
                    .build());
        }

        return sources;
    }
}
