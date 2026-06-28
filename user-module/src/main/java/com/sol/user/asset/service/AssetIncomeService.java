package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetIncomeResponse;
import com.sol.user.asset.dto.AssetIncomeResponse.IncomeSource;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.asset.mapper.AssetMapper;
import com.sol.user.holding.dto.HoldingWithQuantityAndType;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
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

    private static final Set<String> PENSION_ACCOUNT_TYPES = Set.of("IRP", "PENSION_SAVING");

    private final PensionRepository pensionRepository;
    private final HoldingRepository holdingRepository;
    private final AccountRepository accountRepository;
    private final ProductBatchClient productBatchClient;
    private final DepositDetailClient depositDetailClient;
    private final UserRepository userRepository;
    private final AssetMapper assetMapper;

    public AssetIncomeResponse getIncome(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<HoldingWithQuantityAndType> allHoldings =
                holdingRepository.findHoldingsWithQuantityAndTypeByUserId(userId);
        List<Account> accounts = accountRepository.findByUserUserId(userId);
        BigDecimal nationalPension = Boolean.TRUE.equals(user.getNationalPensionReceiving())
                ? calcNationalPension(userId).setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal etfDividend = calcEtfDividend(allHoldings).setScale(0, RoundingMode.HALF_UP);
        BigDecimal depositInterest = calcDepositInterest(accounts).setScale(0, RoundingMode.HALF_UP);
        BigDecimal pensionDividend = calcPensionDividend(allHoldings).setScale(0, RoundingMode.HALF_UP);
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
}
