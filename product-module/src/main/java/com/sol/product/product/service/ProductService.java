package com.sol.product.product.service;

import com.sol.product.product.dto.ProductBatchItem;
import com.sol.product.product.repository.FinancialProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final FinancialProductRepository financialProductRepository;

    public List<ProductBatchItem> getProductsByIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return financialProductRepository.findAllByProductIdIn(productIds).stream()
                .map(ProductBatchItem::from)
                .toList();
    }
}
