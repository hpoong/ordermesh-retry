package com.hopoong.core.api.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopoong.core.api.user.dto.UserResponse;
import com.hopoong.core.keys.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRedisCacheService {

    private static final Duration USER_DETAIL_TTL = Duration.ofSeconds(120);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;


    // get value
    public Optional<UserResponse> getUserDetail(Long userId) {
        String key = RedisKeys.UserDetail.key(userId);

        try {
            byte[] rawValue = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                    connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8))
            );
            if (rawValue == null || rawValue.length == 0) {
                return Optional.empty();
            }

            String jsonValue = new String(rawValue, StandardCharsets.UTF_8);
            return parseUserResponse(jsonValue, key);
        } catch (Exception exception) {
            log.warn("Failed to get user detail cache from Redis. key={}", key, exception);
            return Optional.empty();
        }
    }

    // set value
    public void putUserDetail(Long userId, UserResponse response) {
        String key = RedisKeys.UserDetail.key(userId);

        try {
            String jsonValue = objectMapper.writeValueAsString(response);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = jsonValue.getBytes(StandardCharsets.UTF_8);
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.stringCommands().setEx(keyBytes, USER_DETAIL_TTL.getSeconds(), valueBytes);
                return null;
            });
        } catch (Exception exception) {
            log.warn("Failed to put user detail cache to Redis. key={}", key, exception);
        }
    }

    // delete value
    public void evictUserDetail(Long userId) {
        String key = RedisKeys.UserDetail.key(userId);

        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Failed to evict user detail cache from Redis. key={}", key, exception);
        }
    }

    private Optional<UserResponse> parseUserResponse(String jsonValue, String key) {
        try {
            UserResponse response = objectMapper.readValue(jsonValue, UserResponse.class);
            return Optional.of(response);
        } catch (Exception exception) {
            log.warn("Failed to deserialize user detail cache JSON. key={}", key, exception);
            return Optional.empty();
        }
    }
}
