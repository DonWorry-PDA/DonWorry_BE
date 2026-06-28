package com.sol.user.pension.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.holding.dto.PensionHoldingProjection;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.pension.dto.PensionResourceResponse;
import com.sol.user.pension.dto.PensionResourceResponse.PensionItem;
import com.sol.user.pension.entity.Pension;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PensionResourceService {

    /**
     * 연금 수령 예상액 계산 기준
     * - 연 수익률 3%: 한국 퇴직연금 장기 보수적 운용 수익률 기준
     * - 기대수명 83세: 통계청 한국인 평균 기대수명
     * - IRP/연금저축 개시 나이: 법정 최소 수령 나이 55세 고정
     * 공식: PMT = PV × r / (1 − (1+r)^−n)
     */
    static final BigDecimal ANNUAL_RETURN_RATE = new BigDecimal("0.03");
    static final int EXPECTED_LIFESPAN = 83;
    static final int PENSION_START_AGE = 55;

    /**
     * 퇴직급여 실효세율 추정치 및 연금 수령 할인율.
     * 퇴직소득세 실효세율 8% × 연금 수령 시 할인율(가입기간 기준):
     *   - 10년 이하: 8% × 70% = 5.6%
     *   - 11년 이상: 8% × 60% = 4.8%
     */
    static final BigDecimal RETIREMENT_INCOME_TAX_RATE = new BigDecimal("0.08");
    static final BigDecimal PENSION_DISCOUNT_UNDER_10Y = new BigDecimal("0.70");
    static final BigDecimal PENSION_DISCOUNT_OVER_10Y = new BigDecimal("0.60");

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);

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

        if (!Boolean.TRUE.equals(user.getNationalPensionReceiving())) {
            List<Pension> pensions = pensionRepository.findByUserUserId(userId);
            for (Pension pension : pensions) {
                if (TYPE_NATIONAL.equals(pension.getPensionType())) {
                    items.add(buildNationalItem(pension));
                }
            }
        }

        Map<Long, BigDecimal> holdingsByAccount = buildHoldingMap(userId);
        List<Account> accounts = accountRepository.findByUserUserId(userId);
        for (Account account : accounts) {
            String accountType = account.getAccountType();
            if (TYPE_IRP.equals(accountType)) {
                items.add(buildIrpItem(account, holdingsByAccount));
            } else if (TYPE_PENSION_SAVING.equals(accountType)) {
                items.add(buildPensionSavingItem(account, holdingsByAccount));
            }
        }

        BigDecimal totalGross = items.stream()
                .map(PensionItem::getExpectedMonthlyGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNet = items.stream()
                .map(PensionItem::getExpectedMonthlyNet)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PensionResourceResponse.builder()
                .totalMonthlyPension(totalGross)
                .totalMonthlyPensionNet(totalNet)
                .pensions(items)
                .build();
    }

    private PensionItem buildNationalItem(Pension pension) {
        BigDecimal amount = pension.getExpectedMonthlyAmount();
        return PensionItem.builder()
                .type(TYPE_NATIONAL)
                .label(LABEL_NATIONAL)
                .institutionName(null)
                .startAge(pension.getStartAge())
                .currentBalance(null)
                .retirementAmount(null)
                .personalAmount(null)
                .expectedMonthlyGross(amount)
                .expectedMonthlyNet(amount)
                .effectiveTaxRate(BigDecimal.ZERO)
                .taxBenefitLimit(null)
                .estimated(false)
                .payoutMonths(null)
                .yearsEnrolled(null)
                .build();
    }

    private PensionItem buildIrpItem(Account account,
                                     Map<Long, BigDecimal> holdingsByAccount) {
        BigDecimal deposit = nz(account.getDepositBalance());
        BigDecimal holdingValue = holdingsByAccount.getOrDefault(account.getAccountId(), BigDecimal.ZERO);
        BigDecimal currentBalance = deposit.add(holdingValue);

        int pMonths = payoutMonths(PENSION_START_AGE);
        int yearsEnrolled = calcYearsEnrolled(account.getOpenedAt());

        BigDecimal retirementAmount = account.getIrpRetirementAmount();
        BigDecimal personalAmount = account.getIrpPersonalAmount();

        BigDecimal retirementGross;
        BigDecimal personalGross;

        if (retirementAmount != null && personalAmount != null) {
            retirementGross = calculateMonthlyPmt(retirementAmount, pMonths);
            personalGross = calculateMonthlyPmt(personalAmount, pMonths);
        } else {
            retirementAmount = null;
            personalAmount = null;
            retirementGross = BigDecimal.ZERO;
            personalGross = calculateMonthlyPmt(currentBalance, pMonths);
        }

        BigDecimal grossTotal = retirementGross.add(personalGross);
        BigDecimal retirementTaxRate = retirementEffectiveTaxRate(yearsEnrolled);
        BigDecimal personalTaxRate = PortfolioConstants.PRIVATE_PENSION_TAX_UNDER_70;

        BigDecimal retirementNet = retirementGross.multiply(BigDecimal.ONE.subtract(retirementTaxRate), MC)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal personalNet = personalGross.multiply(BigDecimal.ONE.subtract(personalTaxRate), MC)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal netTotal = retirementNet.add(personalNet);

        BigDecimal effectiveTaxRate = calcEffectiveTaxRate(grossTotal, netTotal);

        return PensionItem.builder()
                .type(TYPE_IRP)
                .label(LABEL_IRP)
                .institutionName(account.getInstitutionName())
                .startAge(PENSION_START_AGE)
                .currentBalance(currentBalance)
                .retirementAmount(retirementAmount)
                .personalAmount(personalAmount)
                .expectedMonthlyGross(grossTotal)
                .expectedMonthlyNet(netTotal)
                .effectiveTaxRate(effectiveTaxRate)
                .taxBenefitLimit(IRP_TAX_LIMIT)
                .estimated(true)
                .payoutMonths(pMonths)
                .yearsEnrolled(yearsEnrolled > 0 ? yearsEnrolled : null)
                .build();
    }

    private PensionItem buildPensionSavingItem(Account account,
                                               Map<Long, BigDecimal> holdingsByAccount) {
        BigDecimal deposit = nz(account.getDepositBalance());
        BigDecimal holdingValue = holdingsByAccount.getOrDefault(account.getAccountId(), BigDecimal.ZERO);
        BigDecimal currentBalance = deposit.add(holdingValue);

        int pMonths = payoutMonths(PENSION_START_AGE);
        BigDecimal gross = calculateMonthlyPmt(currentBalance, pMonths);
        BigDecimal taxRate = PortfolioConstants.PRIVATE_PENSION_TAX_UNDER_70;
        BigDecimal net = gross.multiply(BigDecimal.ONE.subtract(taxRate), MC)
                .setScale(0, RoundingMode.HALF_UP);

        return PensionItem.builder()
                .type(TYPE_PENSION_SAVING)
                .label(LABEL_PENSION_SAVING)
                .institutionName(account.getInstitutionName())
                .startAge(PENSION_START_AGE)
                .currentBalance(currentBalance)
                .retirementAmount(null)
                .personalAmount(null)
                .expectedMonthlyGross(gross)
                .expectedMonthlyNet(net)
                .effectiveTaxRate(taxRate)
                .taxBenefitLimit(PENSION_SAVING_TAX_LIMIT)
                .estimated(true)
                .payoutMonths(pMonths)
                .yearsEnrolled(null)
                .build();
    }

    /**
     * 연금 월 수령액 계산 (PMT 공식)
     * PMT = PV × r × (1+r)^n / ((1+r)^n − 1)
     */
    static BigDecimal calculateMonthlyPmt(BigDecimal balance, int pMonths) {
        if (balance.compareTo(BigDecimal.ZERO) <= 0 || pMonths <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal r = ANNUAL_RETURN_RATE.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal onePlusRpowN = BigDecimal.ONE.add(r).pow(pMonths, MC);
        BigDecimal numerator = r.multiply(onePlusRpowN, MC);
        BigDecimal denominator = onePlusRpowN.subtract(BigDecimal.ONE, MC);
        return balance.multiply(numerator, MC).divide(denominator, 0, RoundingMode.HALF_UP);
    }

    /** 수령 개시 나이부터 기대수명까지의 수령 기간 (개월) */
    static int payoutMonths(int startAge) {
        return (EXPECTED_LIFESPAN - startAge) * 12;
    }

    /** 퇴직급여 실효세율 = 퇴직소득세율 × 연금 수령 할인율 */
    static BigDecimal retirementEffectiveTaxRate(int yearsEnrolled) {
        BigDecimal discount = yearsEnrolled >= 11
                ? PENSION_DISCOUNT_OVER_10Y
                : PENSION_DISCOUNT_UNDER_10Y;
        return RETIREMENT_INCOME_TAX_RATE.multiply(discount, MC);
    }

    /** openedAt → 가입 기간(년). null이면 0 반환 */
    static int calcYearsEnrolled(LocalDate openedAt) {
        if (openedAt == null) return 0;
        return (int) java.time.temporal.ChronoUnit.YEARS.between(openedAt, LocalDate.now());
    }

    /** 블렌디드 실효세율 = 1 - (세후 / 세전). 세전이 0이면 0 반환 */
    private static BigDecimal calcEffectiveTaxRate(BigDecimal gross, BigDecimal net) {
        if (gross.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return BigDecimal.ONE.subtract(
                net.divide(gross, 4, RoundingMode.HALF_UP), MC
        ).setScale(4, RoundingMode.HALF_UP);
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

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
