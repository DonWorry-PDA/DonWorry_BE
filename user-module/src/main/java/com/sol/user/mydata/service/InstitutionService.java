package com.sol.user.mydata.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.assetconnection.entity.AssetConnection;
import com.sol.user.assetconnection.repository.AssetConnectionRepository;
import com.sol.user.mydata.dto.InstitutionResponse;
import com.sol.user.mydata.type.InstitutionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstitutionService {

    private static final Map<String, String> ACCOUNT_TYPE_LABEL = Map.of(
            "CMA", "CMA",
            "DEPOSIT", "예금",
            "BROKERAGE", "ETF",
            "IRP", "IRP",
            "PENSION_SAVING", "연금저축"
    );

    private final AccountRepository accountRepository;
    private final AssetConnectionRepository assetConnectionRepository;

    public List<InstitutionResponse> getInstitutions(Long userId) {
        Set<String> connectedDbNames = assetConnectionRepository.findByUserUserId(userId).stream()
                .filter(c -> "CONNECTED".equals(c.getConnectionStatus()))
                .map(AssetConnection::getInstitutionName)
                .collect(Collectors.toSet());

        Map<String, List<String>> productsByDbName = accountRepository.findByUserUserId(userId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getExistingAccount())
                        && ACCOUNT_TYPE_LABEL.containsKey(a.getAccountType()))
                .collect(Collectors.groupingBy(
                        Account::getInstitutionName,
                        Collectors.mapping(a -> ACCOUNT_TYPE_LABEL.get(a.getAccountType()), Collectors.toList())
                ));

        return InstitutionCode.all().stream()
                .map(code -> {
                    boolean connected = code.getDbNames().stream().anyMatch(connectedDbNames::contains);
                    List<String> products = connected
                            ? code.getDbNames().stream()
                                    .flatMap(dbName -> productsByDbName.getOrDefault(dbName, List.of()).stream())
                                    .distinct()
                                    .toList()
                            : null;
                    return InstitutionResponse.of(code, connected, products);
                })
                .toList();
    }
}
