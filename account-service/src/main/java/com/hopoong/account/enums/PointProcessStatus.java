package com.hopoong.account.enums;

public enum PointProcessStatus {

    READY("대기"),
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
