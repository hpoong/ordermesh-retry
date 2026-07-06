package com.hopoong.processing.consumer.user.point.service;

import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.keys.event.EventLogTypes;
import com.hopoong.core.keys.rabbitmq.RabbitMqKeys;
import com.hopoong.processing.consumer.user.point.exception.UserPointChangedProcessException;
import com.hopoong.processing.consumer.user.point.publisher.FailedMessagePublisher;
import com.hopoong.processing.entity.MessageProcessLog;
import com.hopoong.processing.enums.MessageProcessStatus;
import com.hopoong.processing.repository.MessageProcessLogRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageProcessFailureRecorder {

    private final MessageProcessLogRepository messageProcessLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordRetry(UserPointChangedEvent event, Throwable throwable) {
        int retryCount = nextRetryCount(event);
        MessageProcessLog processLog = baseLog(event)
                .processStatus(MessageProcessStatus.RETRY.name())
                .retryCount(retryCount)
                .build();
        processLog.markRetry(failureReason(throwable), retryCount);
        messageProcessLogRepository.save(processLog);
        return retryCount;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(UserPointChangedEvent event, Throwable throwable, Integer retryCount) {
        MessageProcessLog processLog = baseLog(event)
                .processStatus(MessageProcessStatus.FAILED.name())
                .retryCount(retryCount == null ? 0 : retryCount)
                .build();
        processLog.markFailed(failureReason(throwable));
        messageProcessLogRepository.save(processLog);
    }

    private int nextRetryCount(UserPointChangedEvent event) {
        long count = messageProcessLogRepository.countByEventIdAndConsumerName(
                valueOrUnknown(event.eventId()),
                FailedMessagePublisher.CONSUMER_NAME
        );
        return Math.toIntExact(count + 1);
    }

    private MessageProcessLog.MessageProcessLogBuilder baseLog(UserPointChangedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        String eventId = valueOrUnknown(event.eventId());
        return MessageProcessLog.builder()
                .messageId(eventId)
                .eventId(eventId)
                .eventType(valueOrDefault(event.eventType(), EventLogTypes.USER_POINT_CHANGED))
                .consumerName(FailedMessagePublisher.CONSUMER_NAME)
                .queueName(RabbitMqKeys.UserPointChangedV2.QUEUE)
                .receivedAt(now)
                .duplicateYn("N")
                .traceId(eventId);
    }

    private String failureReason(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && !(current instanceof UserPointChangedProcessException)) {
            current = current.getCause();
        }
        return current;
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
