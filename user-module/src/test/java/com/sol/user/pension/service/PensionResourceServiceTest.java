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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PensionResourceServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private PensionRepository pensionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private UserRepository userRepository;

    private PensionResourceService service;

    @BeforeEach
    void setUp() {
        service = new PensionResourceService(pensionRepository, accountRepository, holdingRepository, userRepository);
        // 국민연금 항목은 '수령 전'일 때만 표시 — 기본 mock(getNationalPensionReceiving=null=수령 전)
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 국민연금
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("국민연금이 있으면 type=NATIONAL, estimated=false, institutionName=null, currentBalance=null")
    void nationalPension_present_correctFields() {
        // Arrange
        Pension national = mockPension("NATIONAL", new BigDecimal("900000"), 63);
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of(national));
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert
        assertThat(response.getPensions()).hasSize(1);
        PensionItem item = response.getPensions().get(0);
        assertThat(item.getType()).isEqualTo("NATIONAL");
        assertThat(item.getLabel()).isEqualTo("국민연금");
        assertThat(item.getInstitutionName()).isNull();
        assertThat(item.getStartAge()).isEqualTo(63);
        assertThat(item.getCurrentBalance()).isNull();
        assertThat(item.getExpectedMonthly()).isEqualByComparingTo("900000");
        assertThat(item.getTaxBenefitLimit()).isNull();
        assertThat(item.isEstimated()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IRP 계좌
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IRP 계좌가 있으면 type=IRP, estimated=true, taxBenefitLimit=9,000,000")
    void irpAccount_present_correctFields() {
        // Arrange
        Account irp = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert
        assertThat(response.getPensions()).hasSize(1);
        PensionItem item = response.getPensions().get(0);
        assertThat(item.getType()).isEqualTo("IRP");
        assertThat(item.getLabel()).isEqualTo("IRP (개인형 퇴직연금)");
        assertThat(item.getInstitutionName()).isEqualTo("신한은행");
        assertThat(item.getStartAge()).isEqualTo(55);
        assertThat(item.getCurrentBalance()).isEqualByComparingTo("12000000");
        assertThat(item.getTaxBenefitLimit()).isEqualByComparingTo("9000000");
        assertThat(item.isEstimated()).isTrue();
    }

    @Test
    @DisplayName("IRP 계좌의 currentBalance = depositBalance + 펀드 평가액 합산")
    void irpAccount_holdingsSummedWithDeposit() {
        // Arrange: depositBalance=12,000,000 + holding evaluation=3,000,000 → 15,000,000
        Account irp = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        PensionHoldingProjection holding = mockHoldingProjection(10L, new BigDecimal("3000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of(holding));

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert
        PensionItem item = response.getPensions().get(0);
        assertThat(item.getCurrentBalance()).isEqualByComparingTo("15000000");
        // expectedMonthly = 15,000,000 / 240 = 62,500
        assertThat(item.getExpectedMonthly()).isEqualByComparingTo("62500");
    }

    @Test
    @DisplayName("IRP expectedMonthly = currentBalance / 240 (HALF_UP 반올림)")
    void irpAccount_expectedMonthly_dividedBy240() {
        // Arrange: 12,000,000 / 240 = 50,000 (정확히 나눠 떨어짐)
        Account irp = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert
        assertThat(response.getPensions().get(0).getExpectedMonthly()).isEqualByComparingTo("50000");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PENSION_SAVING 계좌
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PENSION_SAVING 계좌가 있으면 type=PENSION_SAVING, taxBenefitLimit=6,000,000")
    void pensionSaving_present_correctFields() {
        // Arrange
        Account saving = mockAccount(20L, "PENSION_SAVING", "신한은행", new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(saving));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert
        assertThat(response.getPensions()).hasSize(1);
        PensionItem item = response.getPensions().get(0);
        assertThat(item.getType()).isEqualTo("PENSION_SAVING");
        assertThat(item.getLabel()).isEqualTo("연금저축");
        assertThat(item.getTaxBenefitLimit()).isEqualByComparingTo("6000000");
        assertThat(item.isEstimated()).isTrue();
        // expectedMonthly = 6,000,000 / 240 = 25,000
        assertThat(item.getExpectedMonthly()).isEqualByComparingTo("25000");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // totalMonthlyPension
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("totalMonthlyPension은 모든 expectedMonthly의 합이다")
    void totalMonthlyPension_isSumOfAllExpectedMonthly() {
        // Arrange: NATIONAL=900000, IRP=50000, PENSION_SAVING=25000 → total=975000
        Pension national = mockPension("NATIONAL", new BigDecimal("900000"), 63);
        Account irp = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        Account saving = mockAccount(20L, "PENSION_SAVING", "신한은행", new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of(national));
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp, saving));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert: 900000 + 50000 + 25000 = 975000
        assertThat(response.getTotalMonthlyPension()).isEqualByComparingTo("975000");
        assertThat(response.getPensions()).hasSize(3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 데이터 없음
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("연금 데이터가 전혀 없으면 빈 목록과 totalMonthlyPension=0을 반환한다")
    void noPensionData_emptyListAndZeroTotal() {
        // Arrange
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert
        assertThat(response.getPensions()).isEmpty();
        assertThat(response.getTotalMonthlyPension()).isEqualByComparingTo("0");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 다수 IRP 계좌
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IRP 계좌가 복수이면 각각 별도의 PensionItem으로 반환된다")
    void multipleIrpAccounts_eachGetsOwnPensionItem() {
        // Arrange: IRP 계좌 2개 (계좌 A: 12,000,000, 계좌 B: 6,000,000)
        Account irpA = mockAccount(10L, "IRP", "신한은행", new BigDecimal("12000000"));
        Account irpB = mockAccount(11L, "IRP", "국민은행", new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irpA, irpB));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of());

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert
        assertThat(response.getPensions()).hasSize(2);
        // totalMonthlyPension = 12,000,000/240 + 6,000,000/240 = 50,000 + 25,000 = 75,000
        assertThat(response.getTotalMonthlyPension()).isEqualByComparingTo("75000");
        // 각각의 institutionName이 다름
        List<String> institutions = response.getPensions().stream()
                .map(PensionItem::getInstitutionName)
                .toList();
        assertThat(institutions).containsExactlyInAnyOrder("신한은행", "국민은행");
    }

    @Test
    @DisplayName("IRP 계좌의 holdings는 해당 accountId에 해당하는 것만 합산한다")
    void irpHoldings_summedByAccountId() {
        // Arrange: IRP 계좌 A(id=10), B(id=11) 각각 다른 holding
        Account irpA = mockAccount(10L, "IRP", "신한은행", new BigDecimal("0"));
        Account irpB = mockAccount(11L, "IRP", "국민은행", new BigDecimal("0"));
        PensionHoldingProjection holdingA = mockHoldingProjection(10L, new BigDecimal("12000000"));
        PensionHoldingProjection holdingB = mockHoldingProjection(11L, new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irpA, irpB));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of(holdingA, holdingB));

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert: A = 12,000,000, B = 6,000,000 (holding 합산)
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
        // Arrange: depositBalance=null인 IRP 계좌
        Account irp = mockAccount(10L, "IRP", "신한은행", null);
        PensionHoldingProjection holding = mockHoldingProjection(10L, new BigDecimal("6000000"));
        given(pensionRepository.findByUserUserId(USER_ID)).willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID)).willReturn(List.of(irp));
        given(holdingRepository.findPensionHoldingsByUserId(USER_ID)).willReturn(List.of(holding));

        // Act
        PensionResourceResponse response = service.getPension(USER_ID);

        // Assert: currentBalance = 0 + 6,000,000 = 6,000,000
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

    private PensionHoldingProjection mockHoldingProjection(Long accountId, BigDecimal evaluationAmount) {
        PensionHoldingProjection p = mock(PensionHoldingProjection.class);
        when(p.getAccountId()).thenReturn(accountId);
        when(p.getEvaluationAmount()).thenReturn(evaluationAmount);
        return p;
    }
}
