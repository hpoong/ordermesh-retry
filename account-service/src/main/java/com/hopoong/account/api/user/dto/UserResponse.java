package com.hopoong.account.api.user.dto;

import com.hopoong.account.entity.UserEntity;
import com.hopoong.account.enums.UserStatus;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String loginId,
        String name,
        String email,
        String phone,
        UserStatus status,
        String statusKorean,
        Integer pointBalance,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserResponse from(UserEntity userEntity) {
        return new UserResponse(
                userEntity.getId(),
                userEntity.getLoginId(),
                userEntity.getName(),
                userEntity.getEmail(),
                userEntity.getPhone(),
                userEntity.getStatus(),
                userEntity.getStatus().getKoreanName(),
                userEntity.getPointBalance(),
                userEntity.getLastLoginAt(),
                userEntity.getCreatedAt(),
                userEntity.getUpdatedAt()
        );
    }
}
