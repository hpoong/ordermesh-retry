package com.hopoong.core.exception;

import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.core.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.log.stacktrace:true}")
    private boolean stacktraceEnabled;

    @ExceptionHandler(CoreException.class)
    public ResponseEntity<ErrorResponse> handleCoreException(CoreException ex) {
        logWarnWithOptionalStackTrace("CoreException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(ex.getHttpStatus())
                .body(new ErrorResponse(ex.getResponseCode(), ex.getResponseMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class
    })
    public ResponseEntity<ErrorResponse> handleValidationException(Exception ex) {
        String message;
        if (ex instanceof MethodArgumentNotValidException methodEx) {
            message = methodEx.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
        } else {
            BindException bindEx = (BindException) ex;
            message = bindEx.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
        }

        if (message == null || message.isBlank()) {
            message = "요청값이 올바르지 않습니다.";
        }

        logWarnWithOptionalStackTrace("ValidationException: {}", message, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(CommonResponseCodeEnum.INVALID_REQUEST, message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        logWarnWithOptionalStackTrace("MissingServletRequestParameterException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(CommonResponseCodeEnum.INVALID_REQUEST, "필수 요청 파라미터가 누락되었습니다."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        logWarnWithOptionalStackTrace("HttpMessageNotReadableException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(CommonResponseCodeEnum.INVALID_REQUEST, "요청 본문 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        logWarnWithOptionalStackTrace("HttpRequestMethodNotSupportedException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(CommonResponseCodeEnum.SERVER, "지원하지 않는 메소드 입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        log.error(
                "[ERROR] method={}, uri={}, query={}, clientIp={}, exception={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString() == null ? "" : request.getQueryString(),
                getClientIp(request),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(CommonResponseCodeEnum.SERVER, "알수없는 에러가 발생하였습니다."));
    }

    private void logWarnWithOptionalStackTrace(String format, String message, Exception ex) {
        if (stacktraceEnabled) {
            log.warn(format, message, ex);
            return;
        }
        log.warn(format, message);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
