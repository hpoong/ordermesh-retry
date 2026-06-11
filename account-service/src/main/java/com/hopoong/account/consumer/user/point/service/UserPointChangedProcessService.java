package com.hopoong.account.consumer.user.point.service;

import com.hopoong.account.api.user.dto.UserDetailCacheEvictEvent;
import com.hopoong.account.consumer.user.point.exception.UserPointChangedProcessException;
import com.hopoong.account.entity.PointHistoryEntity;
import com.hopoong.account.entity.UserEntity;
import com.hopoong.account.enums.PointProcessStatus;
import com.hopoong.account.enums.PointType;
import com.hopoong.account.repository.PointHistoryRepository;
import com.hopoong.account.repository.UserRepository;
import com.hopoong.core.event.UserPointChangedEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserPointChangedProcessService {

    private final UserRepository userRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void processV1(UserPointChangedEvent event) {
        validateV1Payload(event);

        if (pointHistoryRepository.existsByEventId(event.eventId())) {
            log.warn(
                    "중복 UserPointChanged 이벤트를 건너뜁니다. eventId={} userId={}",
                    event.eventId(),
                    event.userId()
            );
            return;
        }

        UserEntity userEntity = userRepository.findByIdForUpdate(event.userId())
                .orElseThrow(() -> new UserPointChangedProcessException(
                        "포인트 변경 대상 사용자를 찾을 수 없습니다. userId=" + event.userId()
                ));

        // int expectedBalance = userEntity.getPointBalance() + event.changeAmount();
        // if (expectedBalance != event.balanceAfter()) {
        //     throw new UserPointChangedProcessException(
        //             "포인트 잔액이 일치하지 않습니다. current="
        //                     + userEntity.getPointBalance()
        //                     + ", changeAmount="
        //                     + event.changeAmount()
        //                     + ", balanceAfter="
        //                     + event.balanceAfter()
        //     );
        // }

        LocalDateTime processedAt = LocalDateTime.now();
        userEntity.updatePointBalance(event.balanceAfter());

        PointHistoryEntity pointHistoryEntity = PointHistoryEntity.builder()
                .userId(event.userId())
                .orderId(event.orderId())
                .pointType(PointType.from(event.pointType()).name())
                .pointAmount(event.changeAmount())
                .balanceAfter(event.balanceAfter())
                .processStatus(PointProcessStatus.SUCCESS.name())
                .eventId(event.eventId())
                .processedAt(processedAt)
                .build();

        try {
            pointHistoryRepository.save(pointHistoryEntity);
        } catch (DataIntegrityViolationException exception) {
            log.warn(
                    "동시 중복 UserPointChanged 이벤트를 건너뜁니다. eventId={} userId={}",
                    event.eventId(),
                    event.userId()
            );
            return;
        }

        applicationEventPublisher.publishEvent(new UserDetailCacheEvictEvent(event.userId()));

        log.info(
                "UserPointChanged 이벤트 처리 완료. eventId={} userId={} orderId={} pointType={} changeAmount={} balanceAfter={}",
                event.eventId(),
                event.userId(),
                event.orderId(),
                event.pointType(),
                event.changeAmount(),
                event.balanceAfter()
        );
    }

    private void validateV1Payload(UserPointChangedEvent event) {
        if (event.userId() == null) {
            throw new UserPointChangedProcessException("userId는 필수입니다.");
        }
        if (event.orderId() == null) {
            throw new UserPointChangedProcessException("orderId는 필수입니다.");
        }
        if (event.pointType() == null || event.pointType().isBlank()) {
            throw new UserPointChangedProcessException("pointType은 필수입니다.");
        }
        if (event.changeAmount() == null) {
            throw new UserPointChangedProcessException("changeAmount는 필수입니다.");
        }
        if (event.balanceAfter() == null) {
            throw new UserPointChangedProcessException("balanceAfter는 필수입니다.");
        }
        PointType.from(event.pointType());
    }
}
