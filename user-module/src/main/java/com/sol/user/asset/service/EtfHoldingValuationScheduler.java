package com.sol.user.asset.service;

import com.sol.user.holding.entity.Holding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtfHoldingValuationScheduler {

    private static final String ETF_PRODUCT_TYPE = "ETF";

    private final HoldingRepository holdingRepository;
    private final ProductBatchClient productBatchClient;

    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
    @Transactional
    public void refreshEtfHoldingValuations() {
        List<Holding> holdings = holdingRepository.findAll();
        if (holdings.isEmpty()) {
            return;
        }

        List<Long> productIds = holdings.stream()
                .map(Holding::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ProductBatchItem> products = productBatchClient.fetchProducts(productIds);
        List<Long> etfProductIds = productIds.stream()
                .filter(productId -> isEtf(products.get(productId)))
                .toList();
        if (etfProductIds.isEmpty()) {
            return;
        }

        Map<Long, Long> prices = productBatchClient.fetchEtfPrices(etfProductIds);
        int updated = 0;
        for (Holding holding : holdings) {
            if (!isEtf(products.get(holding.getProductId()))) {
                continue;
            }
            long price = prices.getOrDefault(holding.getProductId(), 0L);
            if (price <= 0) {
                continue;
            }
            holding.refreshValuation(BigDecimal.valueOf(price));
            updated++;
        }

        log.info("ETF holding valuation refresh completed: {} / {} holdings", updated,
                holdings.stream()
                        .filter(holding -> isEtf(products.get(holding.getProductId())))
                        .collect(Collectors.counting()));
    }

    private boolean isEtf(ProductBatchItem product) {
        return product != null && ETF_PRODUCT_TYPE.equals(product.productType());
    }
}
