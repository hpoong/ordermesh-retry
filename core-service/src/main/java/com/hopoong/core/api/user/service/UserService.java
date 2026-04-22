package com.hopoong.core.api.user.service;

import com.hopoong.core.api.user.dto.UserResponse;
import com.hopoong.core.api.user.dto.UserUpdateRequest;
import com.hopoong.core.entity.UserEntity;
import com.hopoong.core.exception.CoreException;
import com.hopoong.core.repository.UserRepository;
import com.hopoong.core.response.CommonResponseCodeEnum;
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

    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        UserEntity userEntity = getUserOrThrow(userId);

        if (userRepository.existsByLoginIdAndIdNot(request.loginId(), userId)) {
            throw CoreException.conflict(CORE_USERS, "이미 사용중인 loginId 입니다.");
        }

        if (userRepository.existsByEmailAndIdNot(request.email(), userId)) {
            throw CoreException.conflict(CORE_USERS, "이미 사용중인 email 입니다.");
        }

        userEntity.updateProfile(
                request.loginId(),
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
}
