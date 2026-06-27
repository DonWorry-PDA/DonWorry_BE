package com.sol.user.pension.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.holding.dto.PensionHoldingProjection;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.pension.dto.PensionResourceResponse;
import com.sol.user.pension.dto.PensionResourceResponse.PensionItem;
import com.sol.user.pension.entity.Pension;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PensionResourceService {

    private static final int PENSION_MONTHS = 240;
    private static final int PENSION_START_AGE = 55;

    private static final BigDecimal IRP_TAX_LIMIT = new BigDecimal("9000000");
    private static final BigDecimal PENSION_SAVING_TAX_LIMIT = new BigDecimal("6000000");

    private static final String TYPE_NATIONAL = "NATIONAL";
    private static final String TYPE_IRP = "IRP";
    private static final String TYPE_PENSION_SAVING = "PENSION_SAVING";

    private static final String LABEL_NATIONAL = "국민연금";
    private static final String LABEL_IRP = "IRP (개인형 퇴직연금)";
    private static final String LABEL_PENSION_SAVING = "연금저축";

    private final PensionRepository pensionRepository;
    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;

    public PensionResourceResponse getPension(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<PensionItem> items = new ArrayList<>();

        // 국민연금 — 수령 전인 경우에만 표시
        // 수령 중이면 /income 섹션에 이미 반영되므로 중복 제거
        if (!Boolean.TRUE.equals(user.getNationalPensionReceiving())) {
            List<Pension> pensions = pensionRepository.findByUserUserId(userId);
            for (Pension pension : pensions) {
                if (TYPE_NATIONAL.equals(pension.getPensionType())) {
                    items.add(buildNationalItem(pension));
                }
            }
        }

        // IRP / PENSION_SAVING 계좌
        Map<Long, BigDecimal> holdingsByAccount = buildHoldingMap(userId);
        List<Account> accounts = accountRepository.findByUserUserId(userId);
        for (Account account : accounts) {
            String accountType = account.getAccountType();
            if (TYPE_IRP.equals(accountType)) {
                items.add(buildAccountItem(account, holdingsByAccount, TYPE_IRP, LABEL_IRP, IRP_TAX_LIMIT));
            } else if (TYPE_PENSION_SAVING.equals(accountType)) {
                items.add(buildAccountItem(account, holdingsByAccount, TYPE_PENSION_SAVING,
                        LABEL_PENSION_SAVING, PENSION_SAVING_TAX_LIMIT));
            }
        }

        BigDecimal total = items.stream()
                .map(PensionItem::getExpectedMonthly)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PensionResourceResponse.builder()
                .totalMonthlyPension(total)
                .pensions(items)
                .build();
    }

    private PensionItem buildNationalItem(Pension pension) {
        return PensionItem.builder()
                .type(TYPE_NATIONAL)
                .label(LABEL_NATIONAL)
                .institutionName(null)
                .startAge(pension.getStartAge())
                .currentBalance(null)
                .expectedMonthly(pension.getExpectedMonthlyAmount())
                .taxBenefitLimit(null)
                .estimated(false)
                .build();
    }

    private PensionItem buildAccountItem(Account account,
                                         Map<Long, BigDecimal> holdingsByAccount,
                                         String type,
                                         String label,
                                         BigDecimal taxBenefitLimit) {
        BigDecimal deposit = account.getDepositBalance() != null
                ? account.getDepositBalance()
                : BigDecimal.ZERO;
        BigDecimal holdingValue = holdingsByAccount.getOrDefault(account.getAccountId(), BigDecimal.ZERO);
        BigDecimal currentBalance = deposit.add(holdingValue);

        BigDecimal expectedMonthly = currentBalance
                .divide(BigDecimal.valueOf(PENSION_MONTHS), 0, RoundingMode.HALF_UP);

        return PensionItem.builder()
                .type(type)
                .label(label)
                .institutionName(account.getInstitutionName())
                .startAge(PENSION_START_AGE)
                .currentBalance(currentBalance)
                .expectedMonthly(expectedMonthly)
                .taxBenefitLimit(taxBenefitLimit)
                .estimated(true)
                .build();
    }

    private Map<Long, BigDecimal> buildHoldingMap(Long userId) {
        List<PensionHoldingProjection> holdings = holdingRepository.findPensionHoldingsByUserId(userId);
        return holdings.stream()
                .collect(Collectors.groupingBy(
                        PensionHoldingProjection::getAccountId,
                        Collectors.reducing(BigDecimal.ZERO,
                                PensionHoldingProjection::getEvaluationAmount,
                                BigDecimal::add)
                ));
    }
}
