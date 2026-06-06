package com.hopoong.account.enums;

import java.util.Arrays;

public enum PointType {

    EARN("적립"),
    CANCEL("취소"),
    EXPIRE("만료");

    private final String koreanName;

    PointType(String koreanName) {
        this.koreanName = koreanName;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public static PointType from(String value) {
        return Arrays.stream(values())
                .filter(pointType -> pointType.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 pointType 입니다: " + value));
    }
}
