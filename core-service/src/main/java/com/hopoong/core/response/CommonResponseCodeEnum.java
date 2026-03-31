package com.hopoong.core.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommonResponseCodeEnum {

    INVALID_REQUEST("T8", "C01"), // T8 : Request
    SERVER("T9", "C01"), // T9 : SERVER
    ;

    private final String type;
    private final String code;
}
