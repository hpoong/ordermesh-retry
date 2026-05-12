package com.hopoong.core.keys.redis;

public final class RedisKeys {

    private RedisKeys() { }

    // 사용자 상세 정보
    public static final class UserDetail {
        public static final String PREFIX = "user:detail:v1:";
        
        public static String key(Long userId) {
            return PREFIX + userId;
        }

        private UserDetail() { }
    }
}
