package com.hopoong.account.api.internal.user;

import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.core.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/users")
public class UserPointChangedInternalController {

    private static final CommonResponseCodeEnum RESPONSE_CODE = CommonResponseCodeEnum.ACCOUNT_USERS;

    private final UserPointApplyService userPointApplyService;

    @PostMapping("/point-changed")
    public SuccessResponse applyPointChanged(@RequestBody UserPointChangedApplyRequest request) {
        userPointApplyService.apply(request);
        return new SuccessResponse(RESPONSE_CODE, null);
    }
}
