package com.hopoong.order.enums;

public enum EventPublishStatus {

    READY("발행 대기"),
    RETRYING("재시도 중"),
    PUBLISHED("발행 완료"),
    FAILED("발행 실패");

    private final String koreanName;

    EventPublishStatus(String koreanName) {
        this.koreanName = koreanName;
    }

    public String getKoreanName() {
        return koreanName;
    }
}
