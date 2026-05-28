package com.hopoong.account.api.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank(message = "productName은 필수입니다.")
        @Size(max = 150, message = "productName은 150자 이하여야 합니다.")
        String productName,

        @NotBlank(message = "productCode는 필수입니다.")
        @Size(max = 50, message = "productCode는 50자 이하여야 합니다.")
        String productCode,

        @NotNull(message = "price는 필수입니다.")
        @DecimalMin(value = "0.00", inclusive = false, message = "price는 0보다 커야 합니다.")
        @Digits(integer = 13, fraction = 2, message = "price 형식이 올바르지 않습니다.")
        BigDecimal price,

        @NotNull(message = "stockQuantity는 필수입니다.")
        @Min(value = 0, message = "stockQuantity는 0 이상이어야 합니다.")
        Integer stockQuantity,

        @NotBlank(message = "status는 필수입니다.")
        @Size(max = 20, message = "status는 20자 이하여야 합니다.")
        String status
) {
}
