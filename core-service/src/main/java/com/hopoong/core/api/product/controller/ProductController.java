package com.hopoong.core.api.product.controller;

import com.hopoong.core.api.product.dto.ProductUpdateRequest;
import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.core.response.SuccessResponse;
import com.hopoong.core.api.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private static final CommonResponseCodeEnum RESPONSE_CODE = CommonResponseCodeEnum.CORE_PRODUCTS;

    @GetMapping("/{productId}")
    public SuccessResponse getProduct(@PathVariable Long productId) {
        return new SuccessResponse(RESPONSE_CODE, productService.getProduct(productId));
    }

    @PutMapping("/{productId}")
    public SuccessResponse updateProduct(@PathVariable Long productId, @Valid @RequestBody ProductUpdateRequest request) {
        return new SuccessResponse(RESPONSE_CODE, productService.updateProduct(productId, request));
    }
}
