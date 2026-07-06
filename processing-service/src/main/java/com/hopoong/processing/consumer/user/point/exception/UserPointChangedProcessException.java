package com.hopoong.processing.consumer.user.point.exception;

public class UserPointChangedProcessException extends RuntimeException {

    public static final String BUSINESS = "BUSINESS";
    public static final String SYSTEM = "SYSTEM";
    public static final String TIMEOUT = "TIMEOUT";

    private final String failureType;
    private final boolean retryable;

    public UserPointChangedProcessException(String message) {
        this(message, BUSINESS, false, null);
    }

    public UserPointChangedProcessException(String message, Throwable cause) {
        this(message, SYSTEM, true, cause);
    }

    public UserPointChangedProcessException(String message, String failureType, boolean retryable) {
        this(message, failureType, retryable, null);
    }

    public UserPointChangedProcessException(String message, String failureType, boolean retryable, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
        this.retryable = retryable;
    }

    public static UserPointChangedProcessException business(String message) {
        return new UserPointChangedProcessException(message, BUSINESS, false);
    }

    public static UserPointChangedProcessException system(String message, Throwable cause) {
        return new UserPointChangedProcessException(message, SYSTEM, true, cause);
    }

    public static UserPointChangedProcessException timeout(String message, Throwable cause) {
        return new UserPointChangedProcessException(message, TIMEOUT, true, cause);
    }

    public String getFailureType() {
        return failureType;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
