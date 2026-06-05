package com.hopoong.account.consumer.user.point.exception;

public class UserPointChangedProcessException extends RuntimeException {

    public UserPointChangedProcessException(String message) {
        super(message);
    }

    public UserPointChangedProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
