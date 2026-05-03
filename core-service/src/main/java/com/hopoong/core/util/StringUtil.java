package com.hopoong.core.util;

public class StringUtil {

    public static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }
}
