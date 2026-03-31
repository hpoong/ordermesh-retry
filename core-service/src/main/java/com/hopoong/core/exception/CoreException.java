package com.hopoong.core.exception;

import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.core.response.ErrorResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CoreException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final CommonResponseCodeEnum responseCode;
    private final String responseMessage;

    public CoreException(HttpStatus httpStatus, CommonResponseCodeEnum responseCode, String responseMessage) {
        super(responseMessage);
        this.httpStatus = httpStatus;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }

    public CoreException(CommonResponseCodeEnum responseCode, String responseMessage) {

        this(HttpStatus.BAD_REQUEST, responseCode, responseMessage);
    }
}
