package com.hopoong.core.response;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SuccessResponse extends CommonResponse {
    private Object data;

    public SuccessResponse(String type, String code, String message, Object data) {
        super(true, type, code, message);
        this.data = data;
    }

    public SuccessResponse(CommonResponseCodeEnum commonResponseCodeEnum, String message) {
        this(commonResponseCodeEnum.getType(), commonResponseCodeEnum.getCode(), message, null);
    }

    public SuccessResponse(CommonResponseCodeEnum commonResponseCodeEnum, Object data) {
        this(commonResponseCodeEnum.getType(), commonResponseCodeEnum.getCode(), "Success", data);
    }
}
