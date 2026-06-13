package com.hopoong.account.api.internal.user;

import com.hopoong.account.api.user.dto.UserDetailCacheEvictEvent;
import com.hopoong.account.entity.UserEntity;
import com.hopoong.account.enums.PointType;
import com.hopoong.account.repository.UserRepository;
import com.hopoong.core.exception.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hopoong.core.response.CommonResponseCodeEnum.ACCOUNT_USERS;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserPointApplyService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void apply(UserPointChangedApplyRequest request) {
        validateRequest(request);

        UserEntity userEntity = userRepository.findByIdForUpdate(request.userId())
                .orElseThrow(() -> CoreException.notFound(ACCOUNT_USERS, "사용자를 찾을 수 없습니다."));

        userEntity.updatePointBalance(request.balanceAfter());
        applicationEventPublisher.publishEvent(new UserDetailCacheEvictEvent(request.userId()));

        log.info(
                "UserPointChanged 잔액 반영 완료. eventId={} userId={} balanceAfter={}",
                request.eventId(),
                request.userId(),
                request.balanceAfter()
        );
    }

    private void validateRequest(UserPointChangedApplyRequest request) {
        if (request.eventId() == null || request.eventId().isBlank()) {
            throw CoreException.badRequest(ACCOUNT_USERS, "eventId는 필수입니다.");
        }
        if (request.userId() == null) {
            throw CoreException.badRequest(ACCOUNT_USERS, "userId는 필수입니다.");
        }
        if (request.orderId() == null) {
            throw CoreException.badRequest(ACCOUNT_USERS, "orderId는 필수입니다.");
        }
        if (request.pointType() == null || request.pointType().isBlank()) {
            throw CoreException.badRequest(ACCOUNT_USERS, "pointType은 필수입니다.");
        }
        if (request.changeAmount() == null) {
            throw CoreException.badRequest(ACCOUNT_USERS, "changeAmount는 필수입니다.");
        }
        if (request.balanceAfter() == null) {
            throw CoreException.badRequest(ACCOUNT_USERS, "balanceAfter는 필수입니다.");
        }
        if (request.occurredAt() == null) {
            throw CoreException.badRequest(ACCOUNT_USERS, "occurredAt은 필수입니다.");
        }
        PointType.from(request.pointType());
    }
}
