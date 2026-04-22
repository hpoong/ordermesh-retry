package com.hopoong.core.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommonResponseCodeEnum {

    // T9 : SERVER
    SERVER("T9", "common"),

    // T8 : Request
    INVALID_REQUEST("T8", "common"),

    // T1 : core-service
    CORE_USERS("T1", "C01"),
    CORE_PRODUCTS("T1", "C02"),

    // T2: order-service

    ;

    private final String type;
    private final String code;
}
