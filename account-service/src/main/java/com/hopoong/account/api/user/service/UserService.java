package com.hopoong.account.api.user.service;

import com.hopoong.account.api.user.dto.UserCreateRequest;
import com.hopoong.account.api.user.dto.UserDetailCacheEvictEvent;
import com.hopoong.account.api.user.dto.UserResponse;
import com.hopoong.account.api.user.dto.UserUpdateRequest;
import com.hopoong.account.entity.UserEntity;
import com.hopoong.account.enums.UserStatus;
import com.hopoong.core.exception.CoreException;
import com.hopoong.account.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hopoong.core.response.CommonResponseCodeEnum.ACCOUNT_USERS;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRedisCacheService userRedisCacheService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return userRedisCacheService.getUserDetail(userId)
                .orElseGet(() -> getUserDetailFromDbAndCache(userId));
    }

    private UserResponse getUserDetailFromDbAndCache(Long userId) {
        UserEntity userEntity = getUserOrThrow(userId);
        UserResponse response = UserResponse.from(userEntity);
        userRedisCacheService.putUserDetail(userId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers(String status, String name, String sortBy, String sortDirection) {
        return userRepository.findUsers(parseUserStatus(status), name, sortBy, sortDirection)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        validateDuplicateForActiveUser(request.loginId(), request.email(), null);

        UserEntity userEntity = UserEntity.builder()
                .loginId(request.loginId())
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .status(UserStatus.ACTIVE)
                .pointBalance(0)
                .build();

        return UserResponse.from(userRepository.save(userEntity));
    }

    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        UserEntity userEntity = getUserOrThrow(userId);
        validateDuplicateForActiveUser(userEntity.getLoginId(), request.email(), userId);

        userEntity.updateProfile(
                request.name(),
                request.email(),
                request.phone()
        );

        UserResponse response = UserResponse.from(userEntity);
        applicationEventPublisher.publishEvent(new UserDetailCacheEvictEvent(userId));
        return response;
    }

    @Transactional
    public void softDeleteUser(Long userId) {
        UserEntity userEntity = getUserOrThrow(userId);

        if (userEntity.getDeletedAt() != null) {
            applicationEventPublisher.publishEvent(new UserDetailCacheEvictEvent(userId));
            return;
        }
        userEntity.softDelete(LocalDateTime.now());
        applicationEventPublisher.publishEvent(new UserDetailCacheEvictEvent(userId));
    }

    private UserStatus parseUserStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UserStatus.from(status.trim());
        } catch (IllegalArgumentException exception) {
            throw CoreException.badRequest(ACCOUNT_USERS, "유효하지 않은 status 입니다. (ACTIVE, INACTIVE, SUSPENDED)");
        }
    }

    private UserEntity getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> CoreException.notFound(ACCOUNT_USERS, "사용자를 찾을 수 없습니다."));
    }

    private void validateDuplicateForActiveUser(String loginId, String email, Long userId) {
        boolean duplicatedLoginId = userId == null
                ? userRepository.existsByLoginIdAndDeletedAtIsNull(loginId)
                : userRepository.existsByLoginIdAndIdNotAndDeletedAtIsNull(loginId, userId);
        if (duplicatedLoginId) {
            throw CoreException.conflict(ACCOUNT_USERS, "이미 사용중인 loginId 입니다.");
        }

        boolean duplicatedEmail = userId == null
                ? userRepository.existsByEmailAndDeletedAtIsNull(email)
                : userRepository.existsByEmailAndIdNotAndDeletedAtIsNull(email, userId);
        if (duplicatedEmail) {
            throw CoreException.conflict(ACCOUNT_USERS, "이미 사용중인 email 입니다.");
        }
    }
}
