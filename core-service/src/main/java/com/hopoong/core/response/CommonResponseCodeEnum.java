package com.hopoong.core.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommonResponseCodeEnum {

    // T9 : SERVER
    SERVER("T9", "common", "서버"),

    // T8 : Request
    INVALID_REQUEST("T8", "common", "잘못된 요청"),

    // T1 : account-service
    ACCOUNT_USERS("T1", "C01", "사용자"),
    ACCOUNT_PRODUCTS("T1", "C02", "상품"),

    // T2: order-service
    ORDER_POINT("T2", "C01", "포인트"),
    ;

    private final String type;
    private final String code;
    private final String koreanName;
}
