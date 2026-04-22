package com.hopoong.core.api.user.dto;

import com.hopoong.core.entity.UserEntity;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String loginId,
        String name,
        String email,
        String phone,
        String status,
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
                userEntity.getPointBalance(),
                userEntity.getLastLoginAt(),
                userEntity.getCreatedAt(),
                userEntity.getUpdatedAt()
        );
    }
}
