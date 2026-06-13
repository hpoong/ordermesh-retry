package com.hopoong.processing.enums;

public enum PointProcessStatus {

    SUCCESS("성공"),
    FAILED("실패");

    private final String koreanName;

    PointProcessStatus(String koreanName) {
        this.koreanName = koreanName;
    }

    public String getKoreanName() {
        return koreanName;
    }
}
