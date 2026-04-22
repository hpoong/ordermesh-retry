package com.hopoong.core.exception;

import com.hopoong.core.response.CommonResponseCodeEnum;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CoreException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final CommonResponseCodeEnum responseCode;
    private final String responseMessage;

    private CoreException(HttpStatus httpStatus, CommonResponseCodeEnum responseCode, String responseMessage) {
        super(responseMessage);
        this.httpStatus = httpStatus;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }

    /** 잘못된 요청 파라미터, 유효하지 않은 입력값 */
    public static CoreException badRequest(CommonResponseCodeEnum responseCode, String responseMessage) {
        return new CoreException(HttpStatus.BAD_REQUEST, responseCode, responseMessage);
    }

    /** 요청한 리소스를 찾을 수 없음 (단건 조회 실패 등) */
    public static CoreException notFound(CommonResponseCodeEnum responseCode, String responseMessage) {
        return new CoreException(HttpStatus.NOT_FOUND, responseCode, responseMessage);
    }

    /** 중복 데이터 충돌 (unique 제약 위반, 이미 존재하는 리소스 등) */
    public static CoreException conflict(CommonResponseCodeEnum responseCode, String responseMessage) {
        return new CoreException(HttpStatus.CONFLICT, responseCode, responseMessage);
    }

    /** 인증되지 않은 요청 (토큰 없음, 만료 등) */
    public static CoreException unauthorized(CommonResponseCodeEnum responseCode, String responseMessage) {
        return new CoreException(HttpStatus.UNAUTHORIZED, responseCode, responseMessage);
    }

    /** 접근 권한 없음 (인증은 됐지만 해당 리소스에 대한 권한 부족) */
    public static CoreException forbidden(CommonResponseCodeEnum responseCode, String responseMessage) {
        return new CoreException(HttpStatus.FORBIDDEN, responseCode, responseMessage);
    }

    /** 현재 리소스 상태에서 허용되지 않는 요청 (주문 취소 불가, 이미 처리된 상태 등) */
    public static CoreException unprocessable(CommonResponseCodeEnum responseCode, String responseMessage) {
        return new CoreException(HttpStatus.UNPROCESSABLE_ENTITY, responseCode, responseMessage);
    }

    /** 서버 내부 오류 (예상치 못한 예외, 외부 시스템 오류 등) */
    public static CoreException internalError(CommonResponseCodeEnum responseCode, String responseMessage) {
        return new CoreException(HttpStatus.INTERNAL_SERVER_ERROR, responseCode, responseMessage);
    }
}
