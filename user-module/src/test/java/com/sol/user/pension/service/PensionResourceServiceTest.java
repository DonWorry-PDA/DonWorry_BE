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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.sol.user.pension.service.PensionResourceService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PensionResourceServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private PensionRepository pensionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private UserRepository userRepository;

    private PensionResourceService service;

    @BeforeEach
    void setUp() {
        service = new PensionResourceService(pensionRepository, accountRepository, holdingRepository, userRepository);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PMT 공식
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("payoutMonths = (기대수명 - 개시나이) × 12")
    void payoutMonths_calculatedFromLifespan() {
        assertThat(payoutMonths(PENSION_START_AGE)).isEqualTo((EXPECTED_LIFESPAN - PENSION_START_AGE) * 12);
    }

    @Test
    @DisplayName("calculateMonthlyPmt: 잔액 0이면 0 반환")
    void calculateMonthlyPmt_zeroBalance_returnsZero() {
        assertThat(calculateMonthlyPmt(BigDecimal.ZERO, 336)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("calculateMonthlyPmt: PMT는 단순 균등분할(잔액/개월)보다 크다 (수익률이 양수이므로)")
    void calculateMonthlyPmt_greaterThanSimpleDivision() {
        BigDecimal balance = new BigDecimal("12000000");
        int pMonths = payoutMonths(PENSION_START_AGE);

        BigDecimal pmt = calculateMonthlyPmt(balance, pMonths);
        BigDecimal simpleDivision = balance.divide(BigDecimal.valueOf(pMonths), 0, java.math.RoundingMode.HALF_UP);

        assertThat(pmt).isGreaterThan(simpleDivision);
    }

    @Test
    @DisplayName("calculateMonthlyPmt: 연 3% 수익률로 IRP 65,000,000 → 약 286,000원/월 (±2,000)")
    void calculateMonthlyPmt_knownValue() {
        BigDecimal pmt = calculateMonthlyPmt(new BigDecimal("65000000"), payoutMonths(PENSION_START_AGE));
        assertThat(pmt.intValue()).isBetween(284_000, 288_000);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 국민연금
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("국민연금이 있으면 type=NATIONAL, estimated=false, payoutMonths=null")
    void nationalPension_present_correctFields() {
        Pension national = mockPension("NATIONAL", new BigDecimal("900000"), 63);
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of(national));
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        assertThat(response.getPensions()).hasSize(1);
        PensionItem item = response.getPensions().get(0);
        assertThat(item.getType()).isEqualTo("NATIONAL");
        assertThat(item.getLabel()).isEqualTo("국민연금");
        assertThat(item.getInstitutionName()).isNull();
        assertThat(item.getStartAge()).isEqualTo(63);
        assertThat(item.getCurrentBalance()).isNull();
        assertThat(item.getExpectedMonthlyGross()).isEqualByComparingTo("900000");
        assertThat(item.getExpectedMonthlyNet()).isEqualByComparingTo("900000");
        assertThat(item.getTaxBenefitLimit()).isNull();
        assertThat(item.isEstimated()).isFalse();
        assertThat(item.getPayoutMonths()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IRP 계좌
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IRP 계좌가 있으면 type=IRP, estimated=true, payoutMonths=336, taxBenefitLimit=9,000,000")
    void irpAccount_present_correctFields() {
        Account irp = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        assertThat(response.getPensions()).hasSize(1);
        PensionItem item = response.getPensions().get(0);
        assertThat(item.getType()).isEqualTo("IRP");
        assertThat(item.getLabel()).isEqualTo("IRP (개인형 퇴직연금)");
        assertThat(item.getInstitutionName()).isEqualTo("신한은행");
        assertThat(item.getStartAge()).isEqualTo(55);
        assertThat(item.getCurrentBalance()).isEqualByComparingTo("12000000");
        assertThat(item.getTaxBenefitLimit()).isEqualByComparingTo("9000000");
        assertThat(item.isEstimated()).isTrue();
        assertThat(item.getPayoutMonths()).isEqualTo(336);
    }

    @Test
    @DisplayName("IRP 계좌의 currentBalance = depositBalance + 펀드 평가액 합산")
    void irpAccount_holdingsSummedWithDeposit() {
        Account irp = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        PensionHoldingProjection holding = mockHoldingProjection(10L, new BigDecimal("3000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of(holding));

        PensionResourceResponse response = service.getPension(USER_ID);

        PensionItem item = response.getPensions().get(0);
        assertThat(item.getCurrentBalance()).isEqualByComparingTo("15000000");
        assertThat(item.getExpectedMonthlyGross()).isPositive();
        assertThat(item.getExpectedMonthlyGross())
                .isGreaterThan(new BigDecimal("15000000").divide(BigDecimal.valueOf(336), 0, java.math.RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("IRP expectedMonthlyGross는 PMT 공식으로 계산된다 (단순 잔액/240 아님)")
    void irpAccount_expectedMonthly_usesPmtNotSimpleDivision() {
        Account irp = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        BigDecimal gross = response.getPensions().get(0).getExpectedMonthlyGross();
        assertThat(gross).isNotEqualByComparingTo(new BigDecimal("50000")); // 12,000,000 / 240
        assertThat(gross).isGreaterThan(new BigDecimal("35714")); // 12,000,000/336
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IRP 세후 계산
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IRP: irpRetirementAmount/personalAmount가 있으면 세후 수령액이 계산된다")
    void irpAccount_withComposition_netMonthlyCalculated() {
        Account irp = mockIrpAccount(10L, "신한은행",
                new BigDecimal("12000000"),
                new BigDecimal("7200000"),
                new BigDecimal("4800000"),
                LocalDate.now().minusYears(15));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        PensionItem item = response.getPensions().get(0);
        assertThat(item.getExpectedMonthlyGross()).isPositive();
        assertThat(item.getExpectedMonthlyNet()).isLessThan(item.getExpectedMonthlyGross());
        assertThat(item.getRetirementAmount()).isEqualByComparingTo("7200000");
        assertThat(item.getPersonalAmount()).isEqualByComparingTo("4800000");
        assertThat(item.getYearsEnrolled()).isEqualTo(15);
        assertThat(item.getEffectiveTaxRate()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    @DisplayName("IRP: irpRetirementAmount가 null이면 전액 개인납입금으로 처리, 세율 5.5%")
    void irpAccount_noComposition_treatedAsPersonal() {
        Account irp = mockIrpAccount(10L, "신한은행",
                new BigDecimal("12000000"), null, null, null);
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        PensionItem item = response.getPensions().get(0);
        BigDecimal gross = item.getExpectedMonthlyGross();
        BigDecimal expectedNet = gross.multiply(new BigDecimal("0.945"))
                .setScale(0, java.math.RoundingMode.HALF_UP);
        assertThat(item.getExpectedMonthlyNet()).isEqualByComparingTo(expectedNet);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PENSION_SAVING 계좌
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PENSION_SAVING 계좌가 있으면 type=PENSION_SAVING, taxBenefitLimit=6,000,000, payoutMonths=336")
    void pensionSaving_present_correctFields() {
        Account saving = mockAccount(20L, "PENSION_SAVING", "신한은행", new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(saving));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        assertThat(response.getPensions()).hasSize(1);
        PensionItem item = response.getPensions().get(0);
        assertThat(item.getType()).isEqualTo("PENSION_SAVING");
        assertThat(item.getLabel()).isEqualTo("연금저축");
        assertThat(item.getTaxBenefitLimit()).isEqualByComparingTo("6000000");
        assertThat(item.isEstimated()).isTrue();
        assertThat(item.getPayoutMonths()).isEqualTo(336);
        assertThat(item.getExpectedMonthlyGross()).isPositive();
    }

    @Test
    @DisplayName("PENSION_SAVING: 전액 개인납입금, 세율 5.5% 적용")
    void pensionSaving_netMonthly_appliesPersonalTaxRate() {
        Account saving = mockAccount(20L, "PENSION_SAVING", "신한은행", new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(saving));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        PensionItem item = response.getPensions().get(0);
        BigDecimal gross = item.getExpectedMonthlyGross();
        BigDecimal expectedNet = gross.multiply(new BigDecimal("0.945"))
                .setScale(0, java.math.RoundingMode.HALF_UP);
        assertThat(item.getExpectedMonthlyNet()).isEqualByComparingTo(expectedNet);
        assertThat(item.getRetirementAmount()).isNull();
        assertThat(item.getPersonalAmount()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // totalMonthlyPension
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("totalMonthlyPension은 모든 expectedMonthlyGross의 합이다")
    void totalMonthlyPension_isSumOfAllExpectedMonthly() {
        Pension national = mockPension("NATIONAL", new BigDecimal("900000"), 63);
        Account irp = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        Account saving = mockAccount(20L, "PENSION_SAVING", "신한은행", new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of(national));
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp, saving));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        BigDecimal expectedTotal = response.getPensions().stream()
                .map(PensionItem::getExpectedMonthlyGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(response.getTotalMonthlyPension()).isEqualByComparingTo(expectedTotal);
        assertThat(response.getPensions()).hasSize(3);
    }

    @Test
    @DisplayName("totalMonthlyPensionNet은 모든 expectedMonthlyNet의 합이다")
    void totalMonthlyPensionNet_isSumOfAllNet() {
        Pension national = mockPension("NATIONAL", new BigDecimal("900000"), 63);
        Account irp = mockIrpAccount(10L, "신한은행",
                new BigDecimal("12000000"), new BigDecimal("7200000"),
                new BigDecimal("4800000"), LocalDate.now().minusYears(15));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of(national));
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        BigDecimal expectedNet = response.getPensions().stream()
                .map(PensionItem::getExpectedMonthlyNet)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(response.getTotalMonthlyPensionNet()).isEqualByComparingTo(expectedNet);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 데이터 없음
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("연금 데이터가 전혀 없으면 빈 목록과 totalMonthlyPension=0을 반환한다")
    void noPensionData_emptyListAndZeroTotal() {
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        assertThat(response.getPensions()).isEmpty();
        assertThat(response.getTotalMonthlyPension()).isEqualByComparingTo("0");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 다수 IRP 계좌
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IRP 계좌가 복수이면 각각 별도의 PensionItem으로 반환된다")
    void multipleIrpAccounts_eachGetsOwnPensionItem() {
        Account irpA = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        Account irpB = mockAccount(11L, "IRP", "국민은행", new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irpA, irpB));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        PensionResourceResponse response = service.getPension(USER_ID);

        assertThat(response.getPensions()).hasSize(2);
        BigDecimal expectedTotal = response.getPensions().stream()
                .map(PensionItem::getExpectedMonthlyGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(response.getTotalMonthlyPension()).isEqualByComparingTo(expectedTotal);
        List<String> institutions = response.getPensions().stream()
                .map(PensionItem::getInstitutionName)
                .toList();
        assertThat(institutions).containsExactlyInAnyOrder("신한은행", "국민은행");
    }

    @Test
    @DisplayName("IRP 계좌의 holdings는 해당 accountId에 해당하는 것만 합산한다")
    void irpHoldings_summedByAccountId() {
        Account irpA = mockAccount(10L, "IRP", "신한은행", new BigDecimal("0"));
        Account irpB = mockAccount(11L, "IRP", "국민은행", new BigDecimal("0"));
        PensionHoldingProjection holdingA = mockHoldingProjection(10L, new BigDecimal("12000000"));
        PensionHoldingProjection holdingB = mockHoldingProjection(11L, new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irpA, irpB));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of(holdingA, holdingB));

        PensionResourceResponse response = service.getPension(USER_ID);

        List<BigDecimal> balances = response.getPensions().stream()
                .map(PensionItem::getCurrentBalance)
                .sorted()
                .toList();
        assertThat(balances.get(0)).isEqualByComparingTo("6000000");
        assertThat(balances.get(1)).isEqualByComparingTo("12000000");
    }

    @Test
    @DisplayName("depositBalance가 null인 계좌는 0으로 처리된다")
    void nullDepositBalance_treatedAsZero() {
        Account irp = mockAccount(10L, "IRP", "신한은행", null);
        PensionHoldingProjection holding = mockHoldingProjection(10L, new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of(holding));

        PensionResourceResponse response = service.getPension(USER_ID);

        assertThat(response.getPensions().get(0).getCurrentBalance()).isEqualByComparingTo("6000000");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Pension mockPension(String pensionType, BigDecimal expectedMonthly, Integer startAge) {
        Pension p = mock(Pension.class);
        when(p.getPensionType()).thenReturn(pensionType);
        when(p.getExpectedMonthlyAmount()).thenReturn(expectedMonthly);
        when(p.getStartAge()).thenReturn(startAge);
        return p;
    }

    private Account mockAccount(Long accountId, String accountType, String institutionName,
                                BigDecimal depositBalance) {
        Account a = mock(Account.class);
        when(a.getAccountId()).thenReturn(accountId);
        when(a.getAccountType()).thenReturn(accountType);
        when(a.getInstitutionName()).thenReturn(institutionName);
        when(a.getDepositBalance()).thenReturn(depositBalance);
        return a;
    }

    private Account mockIrpAccount(Long accountId, String institutionName,
                                    BigDecimal depositBalance,
                                    BigDecimal irpRetirementAmount,
                                    BigDecimal irpPersonalAmount,
                                    LocalDate openedAt) {
        Account a = mock(Account.class);
        when(a.getAccountId()).thenReturn(accountId);
        when(a.getAccountType()).thenReturn("IRP");
        when(a.getInstitutionName()).thenReturn(institutionName);
        when(a.getDepositBalance()).thenReturn(depositBalance);
        when(a.getIrpRetirementAmount()).thenReturn(irpRetirementAmount);
        when(a.getIrpPersonalAmount()).thenReturn(irpPersonalAmount);
        when(a.getOpenedAt()).thenReturn(openedAt);
        return a;
    }

    private PensionHoldingProjection mockHoldingProjection(Long accountId, BigDecimal evaluationAmount) {
        PensionHoldingProjection p = mock(PensionHoldingProjection.class);
        when(p.getAccountId()).thenReturn(accountId);
        when(p.getEvaluationAmount()).thenReturn(evaluationAmount);
        return p;
    }
}
