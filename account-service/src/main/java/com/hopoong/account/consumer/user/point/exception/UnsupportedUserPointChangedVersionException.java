package com.hopoong.account.consumer.user.point.exception;

public class UnsupportedUserPointChangedVersionException extends RuntimeException {

    public UnsupportedUserPointChangedVersionException(String eventVersion) {
        super("지원하지 않는 UserPointChanged 이벤트 버전입니다: " + eventVersion);
    }
}
