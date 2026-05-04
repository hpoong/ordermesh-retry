package com.hopoong.core.keys.redis;

public final class RedisKeys {

    private static final String CORE_USER_DETAIL_PREFIX = "core:user:detail:v1:v1:";

    private RedisKeys() { }

    public static String userDetail(Long userId) {
        return CORE_USER_DETAIL_PREFIX + userId;
    }
}
