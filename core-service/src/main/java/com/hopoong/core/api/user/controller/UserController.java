package com.hopoong.core.api.user.controller;

import com.hopoong.core.api.user.dto.UserCreateRequest;
import com.hopoong.core.api.user.dto.UserUpdateRequest;
import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.core.response.SuccessResponse;
import com.hopoong.core.api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private static final CommonResponseCodeEnum RESPONSE_CODE = CommonResponseCodeEnum.CORE_USERS;

    @GetMapping("/{userId}")
    public SuccessResponse getUser(@PathVariable Long userId) {
        return new SuccessResponse(RESPONSE_CODE, userService.getUser(userId));
    }

    @PostMapping
    public SuccessResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        return new SuccessResponse(RESPONSE_CODE, userService.createUser(request));
    }

    @PutMapping("/{userId}")
    public SuccessResponse updateUser(@PathVariable Long userId, @Valid @RequestBody UserUpdateRequest request) {
        return new SuccessResponse(RESPONSE_CODE, userService.updateUser(userId, request));
    }
}
