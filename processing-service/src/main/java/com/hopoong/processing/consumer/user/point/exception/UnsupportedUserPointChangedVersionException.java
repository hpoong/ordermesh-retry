package com.hopoong.processing.consumer.user.point.exception;

public class UnsupportedUserPointChangedVersionException extends RuntimeException {

    public UnsupportedUserPointChangedVersionException(String eventVersion) {
        super("지원하지 않는 UserPointChanged eventVersion 입니다. eventVersion=" + eventVersion);
    }
}
