package com.hopoong.core.api.product.dto;

import com.hopoong.core.entity.ProductEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String productName,
        String productCode,
        BigDecimal price,
        Integer stockQuantity,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProductResponse from(ProductEntity productEntity) {
        return new ProductResponse(
                productEntity.getId(),
                productEntity.getProductName(),
                productEntity.getProductCode(),
                productEntity.getPrice(),
                productEntity.getStockQuantity(),
                productEntity.getStatus(),
                productEntity.getCreatedAt(),
                productEntity.getUpdatedAt()
        );
    }
}
