package com.hopoong.core.api.user.dto;

/**
 * 사용자 상세 정보 Redis 캐시를 제거하기 위한 이벤트
 *
 * @param userId 캐시를 삭제할 대상 사용자 ID
 */
public record UserDetailCacheEvictEvent(Long userId) {
}
