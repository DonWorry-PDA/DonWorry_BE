package com.sol.user.mydata.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.assetconnection.entity.AssetConnection;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.mydata.dto.InstitutionConnectResponse;
import com.sol.user.mydata.dto.InstitutionResponse;
import com.sol.user.mydata.type.InstitutionCode;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstitutionService {

    private final AccountRepository accountRepository;
    private final AssetConnectionRepository assetConnectionRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<InstitutionResponse> getInstitutions(Long userId) {
        Set<String> connectedDbNames = assetConnectionRepository.findByUserUserId(userId).stream()
                .filter(c -> "CONNECTED".equals(c.getConnectionStatus()))
                .map(AssetConnection::getInstitutionName)
                .collect(Collectors.toSet());

        List<Account> existingAccounts = accountRepository.findByUserUserId(userId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getExistingAccount()))
                .toList();

        existingAccounts.stream()
                .filter(a -> a.getDisplayNumber() == null)
                .forEach(a -> a.updateDisplayNumber(generateDisplayNumber()));

        existingAccounts.stream()
                .map(Account::getInstitutionName)
                .forEach(connectedDbNames::add);

        Map<String, List<String>> displayNumbersByDbName = existingAccounts.stream()
                .filter(a -> a.getDisplayNumber() != null)
                .collect(Collectors.groupingBy(
                        Account::getInstitutionName,
                        Collectors.mapping(Account::getDisplayNumber, Collectors.toList())
                ));

        Map<String, Long> totalByDbName = existingAccounts.stream()
                .filter(a -> a.getDepositBalance() != null)
                .collect(Collectors.groupingBy(
                        Account::getInstitutionName,
                        Collectors.summingLong(a -> a.getDepositBalance().longValue())
                ));

        return InstitutionCode.all().stream()
                .map(code -> {
                    boolean connected = code.getDbNames().stream().anyMatch(connectedDbNames::contains);
                    List<String> accountNumbers = connected
                            ? code.getDbNames().stream()
                                    .flatMap(dbName -> displayNumbersByDbName.getOrDefault(dbName, List.of()).stream())
                                    .toList()
                            : null;
                    Long totalAmountKrw = connected
                            ? code.getDbNames().stream()
                                    .mapToLong(dbName -> totalByDbName.getOrDefault(dbName, 0L))
                                    .sum()
                            : null;
                    return InstitutionResponse.of(code, connected, accountNumbers, totalAmountKrw);
                })
                .toList();
    }

    @Transactional
    public InstitutionConnectResponse connect(Long userId, List<String> institutionIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        List<InstitutionCode> codes = institutionIds.stream()
                .distinct()
                .map(id -> InstitutionCode.fromId(id)
                        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT)))
                .toList();

        Map<String, AssetConnection> existingByDbName = assetConnectionRepository.findByUserUserId(userId).stream()
                .collect(Collectors.toMap(AssetConnection::getInstitutionName, c -> c, (a, b) -> a));

        Map<String, List<Account>> accountsByInstitution = accountRepository.findByUserUserId(userId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getExistingAccount()))
                .collect(Collectors.groupingBy(Account::getInstitutionName));

        Set<String> existingAccountInstitutions = accountsByInstitution.keySet();

        LocalDateTime now = LocalDateTime.now();
        List<AssetConnection> connectionsToSave = new ArrayList<>();
        List<Account> accountsToSave = new ArrayList<>();

        for (InstitutionCode code : codes) {
            String category = "bank".equals(code.getType()) ? "BANK" : "SECURITIES";
            for (String dbName : code.getDbNames()) {
                AssetConnection existing = existingByDbName.get(dbName);
                if (existing != null) {
                    existing.updateMock(dbName, now);
                    connectionsToSave.add(existing);
                    accountsByInstitution.getOrDefault(dbName, List.of()).stream()
                            .filter(a -> a.getDisplayNumber() == null)
                            .forEach(a -> a.updateDisplayNumber(generateDisplayNumber()));
                } else {
                    connectionsToSave.add(new AssetConnection(user, dbName, category, "CONNECTED", now));
                    if (!existingAccountInstitutions.contains(dbName)) {
                        String accountType = dbName.endsWith("증권") ? "BROKERAGE" : "DEPOSIT";
                        accountsToSave.add(Account.createMockExternal(user, accountType, dbName,
                                generateDisplayNumber(), generateRandomBalance()));
                    }
                }
            }
        }

        assetConnectionRepository.saveAll(connectionsToSave);
        accountRepository.saveAll(accountsToSave);
        return new InstitutionConnectResponse(codes.size());
    }

    private BigDecimal generateRandomBalance() {
        long amount = ThreadLocalRandom.current().nextLong(10, 3001) * 10_000;
        return BigDecimal.valueOf(amount);
    }

    private String generateDisplayNumber() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format("%03d-%04d-%06d",
                random.nextInt(100, 1000),
                random.nextInt(1000, 10000),
                random.nextInt(100000, 1000000));
    }
}
