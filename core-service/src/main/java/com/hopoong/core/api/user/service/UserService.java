package com.hopoong.core.api.user.service;

import com.hopoong.core.api.user.dto.UserCreateRequest;
import com.hopoong.core.api.user.dto.UserResponse;
import com.hopoong.core.api.user.dto.UserUpdateRequest;
import com.hopoong.core.entity.UserEntity;
import com.hopoong.core.exception.CoreException;
import com.hopoong.core.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hopoong.core.response.CommonResponseCodeEnum.CORE_USERS;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        UserEntity userEntity = getUserOrThrow(userId);
        return UserResponse.from(userEntity);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers(String status, String name, String sortBy, String sortDirection) {
        return userRepository.findUsers(status, name, sortBy, sortDirection)
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
                .status("ACTIVE")
                .pointBalance(0)
                .build();

        return UserResponse.from(userRepository.save(userEntity));
    }

    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        UserEntity userEntity = getUserOrThrow(userId);
        validateDuplicateForActiveUser(request.loginId(), request.email(), userId);

        userEntity.updateProfile(
                request.name(),
                request.email(),
                request.phone(),
                request.status()
        );

        return UserResponse.from(userEntity);
    }

    private UserEntity getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> CoreException.notFound(CORE_USERS, "사용자를 찾을 수 없습니다."));
    }

    private void validateDuplicateForActiveUser(String loginId, String email, Long userId) {
        boolean duplicatedLoginId = userId == null
                ? userRepository.existsByLoginIdAndDeletedAtIsNull(loginId)
                : userRepository.existsByLoginIdAndIdNotAndDeletedAtIsNull(loginId, userId);
        if (duplicatedLoginId) {
            throw CoreException.conflict(CORE_USERS, "이미 사용중인 loginId 입니다.");
        }

        boolean duplicatedEmail = userId == null
                ? userRepository.existsByEmailAndDeletedAtIsNull(email)
                : userRepository.existsByEmailAndIdNotAndDeletedAtIsNull(email, userId);
        if (duplicatedEmail) {
            throw CoreException.conflict(CORE_USERS, "이미 사용중인 email 입니다.");
        }
    }
}
