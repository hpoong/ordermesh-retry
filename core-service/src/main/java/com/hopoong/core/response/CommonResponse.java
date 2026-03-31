package com.hopoong.core.response;

import com.hopoong.core.util.TimeUtil;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommonResponse {

    private Boolean success;
    private String type;
    private String code;
    private String message;
    private String timestamp;

    public CommonResponse(Boolean success,String type, String code, String message) {
        this.success = success;
        this.type = type;
        this.code = code;
        this.message = message;
        this.timestamp = TimeUtil.getFormattedTimestamp("yyyy-MM-dd HH:mm:ss.SSS");
    }

    public CommonResponse(Boolean success, CommonResponseCodeEnum commonResponseCodeEnum, String message) {
        this(
                success,
                commonResponseCodeEnum.getType(),
                commonResponseCodeEnum.getCode(),
                message
        );
    }

}
