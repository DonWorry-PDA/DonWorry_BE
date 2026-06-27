package com.sol.user.mydata.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.assetconnection.domain.ConnectedInstitutions;
import com.sol.user.assetconnection.entity.AssetConnection;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.mydata.dto.ConnectedInstitutionCountResponse;
import com.sol.user.mydata.dto.ConnectedInstitutionResponse;
import com.sol.user.mydata.dto.ConnectedInstitutionsResponse;
import com.sol.user.mydata.dto.InstitutionConnectResponse;
import com.sol.user.mydata.dto.InstitutionResponse;
import com.sol.user.mydata.type.InstitutionCategory;
import com.sol.user.mydata.type.InstitutionCode;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    public List<InstitutionResponse> getInstitutions(Long userId) {
        // 연결 판정의 진실 소스는 AssetConnection뿐이다(#204). 계좌 보유는 연결의 '결과'지
        // '근거'가 아니므로 connected 플래그에 섞지 않는다. 계좌는 아래 계좌번호·잔액 표시에만 쓴다.
        Set<String> connectedDbNames = assetConnectionRepository.findByUserUserId(userId).stream()
                .filter(c -> "CONNECTED".equals(c.getConnectionStatus()))
                .map(AssetConnection::getInstitutionName)
                .collect(Collectors.toSet());

        List<Account> existingAccounts = accountRepository.findByUserUserId(userId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getExistingAccount()))
                .toList();

        Map<String, List<String>> displayNumbersByDbName = existingAccounts.stream()
                .filter(a -> a.getDisplayNumber() != null)
                .collect(Collectors.groupingBy(
                        Account::getInstitutionName,
                        Collectors.mapping(Account::getDisplayNumber, Collectors.toList())
                ));

        Map<String, BigDecimal> totalByDbName = existingAccounts.stream()
                .filter(a -> a.getDepositBalance() != null)
                .collect(Collectors.groupingBy(
                        Account::getInstitutionName,
                        Collectors.reducing(BigDecimal.ZERO, Account::getDepositBalance, BigDecimal::add)
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
                                    .map(dbName -> totalByDbName.getOrDefault(dbName, BigDecimal.ZERO))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .longValue()
                            : null;
                    return InstitutionResponse.of(code, connected, accountNumbers, totalAmountKrw);
                })
                .toList();
    }

    /**
     * 연결된 기관 수(#204). 온보딩 응답과 동일한 정의({@link ConnectedInstitutions})로 산출해
     * 양 화면이 항상 같은 값을 내도록 한다. 진실 소스는 카탈로그가 아니라 AssetConnection 테이블이다.
     */
    public ConnectedInstitutionCountResponse getConnectedInstitutionCount(Long userId) {
        long count = ConnectedInstitutions.count(assetConnectionRepository.findByUserUserId(userId));
        return new ConnectedInstitutionCountResponse((int) count);
    }

    /**
     * 연결된 기관 목록(#211). 진실 소스 = {@code AssetConnection}(CONNECTED), institutionName distinct로
     * {@link ConnectedInstitutions#count}와 동일 모집단이라 길이 == connected-count 불변식이 성립한다.
     * 한 기관이 여러 category면 우선순위가 높은 하나를 대표로 골라 1행으로 합친다(신한은행 BANK+LOAN → BANK).
     */
    public ConnectedInstitutionsResponse getConnectedInstitutions(Long userId) {
        Map<String, List<AssetConnection>> byName = assetConnectionRepository.findByUserUserId(userId).stream()
                .filter(c -> "CONNECTED".equals(c.getConnectionStatus()))
                .collect(Collectors.groupingBy(AssetConnection::getInstitutionName));

        List<ConnectedInstitutionResponse> institutions = byName.entrySet().stream()
                .map(e -> toResponse(e.getKey(), representativeCategory(e.getValue())))
                .sorted(Comparator.comparingInt(r -> InstitutionCategory.from(r.category())
                        .map(InstitutionCategory::priority).orElse(Integer.MAX_VALUE)))
                .toList();

        return ConnectedInstitutionsResponse.of(institutions);
    }

    /** 한 기관의 여러 연결 category 중 우선순위가 가장 높은(ordinal 최소) 대표 category를 고른다. */
    private String representativeCategory(List<AssetConnection> connections) {
        return connections.stream()
                .map(AssetConnection::getCategory)
                .min(Comparator.comparingInt(category -> InstitutionCategory.from(category)
                        .map(InstitutionCategory::priority).orElse(Integer.MAX_VALUE)))
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));
    }

    /** 표시 메타: 기관명이 카탈로그(dbName)와 매칭되면 카탈로그값, 아니면 category 폴백을 쓴다. */
    private ConnectedInstitutionResponse toResponse(String institutionName, String category) {
        return InstitutionCode.byDbName(institutionName)
                .map(code -> new ConnectedInstitutionResponse(institutionName, category, "CONNECTED",
                        code.getLabel(), code.getBrandColor(), code.getLabelColor()))
                .orElseGet(() -> {
                    InstitutionCategory meta = InstitutionCategory.from(category).orElse(null);
                    String label = meta != null ? meta.getFallbackLabel() : institutionName;
                    String brandColor = meta != null ? meta.getFallbackBrandColor() : "#6B7280";
                    String labelColor = meta != null ? meta.getFallbackLabelColor() : "#FFFFFF";
                    return new ConnectedInstitutionResponse(institutionName, category, "CONNECTED",
                            label, brandColor, labelColor);
                });
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
                    List<Account> dbAccounts = accountsByInstitution.getOrDefault(dbName, List.of());
                    if (dbAccounts.isEmpty()) {
                        String accountType = "securities".equals(code.getType()) ? "BROKERAGE" : "DEPOSIT";
                        accountsToSave.add(Account.createMockExternal(user, accountType, dbName,
                                generateDisplayNumber(), generateRandomBalance()));
                    } else {
                        dbAccounts.stream()
                                .filter(a -> a.getDisplayNumber() == null)
                                .forEach(a -> a.updateDisplayNumber(generateDisplayNumber()));
                    }
                } else {
                    connectionsToSave.add(new AssetConnection(user, dbName, category, "CONNECTED", now));
                    if (!existingAccountInstitutions.contains(dbName)) {
                        String accountType = "securities".equals(code.getType()) ? "BROKERAGE" : "DEPOSIT";
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
