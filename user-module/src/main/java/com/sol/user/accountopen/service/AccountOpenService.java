package com.sol.user.accountopen.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.accountopen.dto.AccountOpenRequest;
import com.sol.user.accountopen.dto.AccountOpenResponse;
import com.sol.user.accountopen.dto.IdentityResponse;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccountOpenService {

    private static final List<String> REQUIRED_TERM_IDS = List.of(
            "account", "deposit", "account-privacy", "account-third-party"
    );
    private static final String ACCOUNT_TYPE = "DON_WORRY";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final OtpService otpService;

    public void validateTerms(List<String> agreedTermIds) {
        Set<String> agreed = Set.copyOf(agreedTermIds);
        boolean allAgreed = REQUIRED_TERM_IDS.stream().allMatch(agreed::contains);
        if (!allAgreed) {
            throw new BaseException(ErrorCode.TERMS_NOT_AGREED);
        }
    }

    @Transactional(readOnly = true)
    public IdentityResponse getIdentity(Long userId) {
        User user = findUser(userId);
        return IdentityResponse.builder()
                .name(user.getName())
                .idNumberMasked(maskIdNumber(user.getIdNumber()))
                .phone(user.getPhone())
                .build();
    }

    @Transactional
    public AccountOpenResponse openAccount(Long userId, AccountOpenRequest request) {
        validateTerms(request.getAgreedTermIds());

        if (!otpService.isVerified(userId)) {
            throw new BaseException(ErrorCode.OTP_NOT_VERIFIED);
        }

        if (accountRepository.existsByUserUserIdAndAccountType(userId, ACCOUNT_TYPE)) {
            throw new BaseException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        User user = findUser(userId);
        LocalDate today = LocalDate.now();
        String accountNumber = generateAccountNumber();

        Account account = Account.createDonWorry(user, accountNumber, today);
        accountRepository.save(account);

        otpService.clearVerified(userId);

        return AccountOpenResponse.builder()
                .accountNumber(accountNumber)
                .openedAt(today.format(DATE_FORMATTER))
                .build();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    }

    private String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.length() < 6) {
            return null;
        }
        return idNumber.substring(0, 6) + "-●●●●●●●";
    }

    private String generateAccountNumber() {
        int middle = 100 + secureRandom.nextInt(900);
        int last = 100000 + secureRandom.nextInt(900000);
        return String.format("110-%03d-%06d", middle, last);
    }
}
