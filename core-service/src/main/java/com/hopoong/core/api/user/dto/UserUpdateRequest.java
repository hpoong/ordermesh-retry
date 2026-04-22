package com.hopoong.core.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank(message = "loginId는 필수입니다.")
        @Size(max = 50, message = "loginId는 50자 이하여야 합니다.")
        String loginId,

        @NotBlank(message = "name은 필수입니다.")
        @Size(max = 100, message = "name은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "email 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email,

        @Size(max = 30, message = "phone은 30자 이하여야 합니다.")
        String phone,

        @NotBlank(message = "status는 필수입니다.")
        @Size(max = 20, message = "status는 20자 이하여야 합니다.")
        String status
) {
}
