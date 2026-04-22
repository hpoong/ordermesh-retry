package com.hopoong.core.api.product.service;

import com.hopoong.core.api.product.dto.ProductResponse;
import com.hopoong.core.api.product.dto.ProductUpdateRequest;
import com.hopoong.core.entity.ProductEntity;
import com.hopoong.core.exception.CoreException;
import com.hopoong.core.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hopoong.core.response.CommonResponseCodeEnum.CORE_PRODUCTS;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        ProductEntity productEntity = getProductOrThrow(productId);
        return ProductResponse.from(productEntity);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductUpdateRequest request) {
        ProductEntity productEntity = getProductOrThrow(productId);

        if (productRepository.existsByProductCodeAndIdNot(request.productCode(), productId)) {
            throw CoreException.conflict(CORE_PRODUCTS, "이미 사용중인 productCode 입니다.");
        }

        productEntity.updateInfo(
                request.productName(),
                request.productCode(),
                request.price(),
                request.stockQuantity(),
                request.status()
        );

        return ProductResponse.from(productEntity);
    }

    private ProductEntity getProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> CoreException.notFound(CORE_PRODUCTS, "상품을 찾을 수 없습니다."));
    }
}
