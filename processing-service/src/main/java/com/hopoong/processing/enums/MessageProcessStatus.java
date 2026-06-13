package com.hopoong.processing.enums;

public enum MessageProcessStatus {

    RECEIVED("수신"),
    PROCESSING("처리중"),
    SUCCESS("성공"),
    FAILED("실패"),
    DUPLICATE("중복"),
    RETRY("재시도"),
    DLQ("DLQ");

    private final String koreanName;

    MessageProcessStatus(String koreanName) {
        this.koreanName = koreanName;
    }

    public String getKoreanName() {
        return koreanName;
    }
}
