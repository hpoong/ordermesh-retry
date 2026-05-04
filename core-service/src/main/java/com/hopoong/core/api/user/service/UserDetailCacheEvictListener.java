package com.hopoong.core.api.user.service;

import com.hopoong.core.api.user.dto.UserDetailCacheEvictEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserDetailCacheEvictListener {

    private final UserRedisCacheService userRedisCacheService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserDetailCacheEvict(UserDetailCacheEvictEvent event) {
        userRedisCacheService.evictUserDetail(event.userId());
    }
}


