package com.hopoong.recovery.enums;

import java.util.Arrays;

public enum FailureType {

    BUSINESS("비즈니스"),
    SYSTEM("시스템"),
    TIMEOUT("타임아웃");

    private final String koreanName;

    FailureType(String koreanName) {
        this.koreanName = koreanName;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public static FailureType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 실패 유형입니다: " + value));
    }
}
