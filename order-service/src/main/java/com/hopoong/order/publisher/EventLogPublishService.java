package com.hopoong.order.publisher;

import com.hopoong.order.config.properties.EventOutboxPublishProperties;
import com.hopoong.order.entity.EventLog;
import com.hopoong.order.enums.EventPublishStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogPublishService {

    // [스케줄러가 조회·발행 대상으로 삼는 상태(READY/RETRYING)]
    private static final List<EventPublishStatus> PUBLISH_CANDIDATE_STATUSES = List.of(
            EventPublishStatus.READY,
            EventPublishStatus.RETRYING
    );

    private final EventLogPublishTransactionService eventLogPublishTransactionService;
    private final UserPointChangedEventPublisher userPointChangedEventPublisher;
    private final EventOutboxPublishProperties publishProperties;

    // [READY/RETRYING 후보 조회 → 선점 → MQ 발행 → 성공 시 PUBLISHED 반영(배치 상한까지)]
    public int publishReadyEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<EventLog> candidates = eventLogPublishTransactionService.findPublishCandidates(
                PUBLISH_CANDIDATE_STATUSES,
                now
        );
        int publishedCount = 0;

        for (EventLog candidate : candidates) {
            if (publishedCount >= publishProperties.getBatchSize()) {
                break;
            }

            // [재검증 + 재시도 카운터/상태 갱신]
            EventLog claimedEventLog = eventLogPublishTransactionService.claimForPublish(
                    candidate.getId(),
                    now,
                    PUBLISH_CANDIDATE_STATUSES
            );

            if (claimedEventLog == null) {
                continue;
            }

            try {
                userPointChangedEventPublisher.publish(claimedEventLog);

//                // 디버깅용 강제 장애: MQ 발행 성공 후 DB 성공 반영(markPublished) 이전 실패 시나리오 재현
//                throw new IllegalStateException("DEBUG: force failure after publish before markPublished");
                
                eventLogPublishTransactionService.markPublished(claimedEventLog.getId(), now);
                publishedCount++;
                log.info(
                        "이벤트 발행 완료 eventId={} eventType={} routingKey={}",
                        claimedEventLog.getEventId(),
                        claimedEventLog.getEventType(),
                        claimedEventLog.getRoutingKey()
                );
            } catch (RuntimeException exception) {
                handlePublishFailure(claimedEventLog, exception, now);
            }
        }

        return publishedCount;
    }

    // [발행 예외 시 DB에 실패 반영: 재시도 가능이면 RETRYING + nextRetryAt, 아니면 FAILED]
    private void handlePublishFailure(EventLog claimedEventLog, RuntimeException exception, LocalDateTime now) {
        String failureReason = truncateFailureReason(exception.getMessage());

        LocalDateTime nextRetryAt = claimedEventLog.getPublishAttemptCount() >= publishProperties.getMaxAttempts()
                ? null
                : now.plusSeconds(publishProperties.getRetryDelaySeconds());

        EventLog eventLog = eventLogPublishTransactionService.markPublishFailure(
                claimedEventLog.getId(),
                failureReason,
                now,
                nextRetryAt
        );

        if (nextRetryAt == null) {
            log.error(
                    "이벤트 발행 최종 실패 eventId={} eventType={} attemptCount={}",
                    eventLog.getEventId(),
                    eventLog.getEventType(),
                    eventLog.getPublishAttemptCount(),
                    exception
            );
            return;
        }

        log.warn(
                "이벤트 발행 재시도 예약 eventId={} eventType={} attemptCount={} nextRetryAt={}",
                eventLog.getEventId(),
                eventLog.getEventType(),
                eventLog.getPublishAttemptCount(),
                nextRetryAt,
                exception
        );
    }

    // [저장 컬럼 길이(255)에 맞춰 실패 사유 축약]
    private String truncateFailureReason(String failureReason) {
        if (!StringUtils.hasText(failureReason)) {
            return "RabbitMQ publish failed";
        }

        return failureReason.length() > 255 ? failureReason.substring(0, 255) : failureReason;
    }
}
