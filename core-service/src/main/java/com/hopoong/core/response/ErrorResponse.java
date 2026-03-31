package com.hopoong.core.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorResponse extends CommonResponse {

    public ErrorResponse(String type, String code, String message) {
        super(false, type, code, message);
    }

    public ErrorResponse(CommonResponseCodeEnum commonResponseCodeEnum, String message) {
        this(commonResponseCodeEnum.getType(), commonResponseCodeEnum.getCode(), message);
    }
}
