package com.hopoong.recovery.enums;

import java.util.Arrays;

public enum ReprocessStatus {

    WAITING("대기"),
    PROCESSING("처리중"),
    SUCCESS("성공"),
    FAILED("실패");

    private final String koreanName;

    ReprocessStatus(String koreanName) {
        this.koreanName = koreanName;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public static ReprocessStatus from(String value) {
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 재처리 상태입니다: " + value));
    }
}
