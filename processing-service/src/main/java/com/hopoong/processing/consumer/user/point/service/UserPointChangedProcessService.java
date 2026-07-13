package com.hopoong.processing.consumer.user.point.service;

import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.keys.event.EventLogTypes;
import com.hopoong.core.keys.rabbitmq.RabbitMqKeys;
import com.hopoong.processing.consumer.user.point.client.AccountPointApplyClient;
import com.hopoong.processing.consumer.user.point.exception.UserPointChangedProcessException;
import com.hopoong.processing.consumer.user.point.publisher.FailedMessagePublisher;
import com.hopoong.processing.entity.MessageProcessLog;
import com.hopoong.processing.entity.PointHistory;
import com.hopoong.processing.enums.MessageProcessStatus;
import com.hopoong.processing.enums.PointProcessStatus;
import com.hopoong.processing.enums.PointType;
import com.hopoong.processing.repository.MessageProcessLogRepository;
import com.hopoong.processing.repository.PointHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserPointChangedProcessService {

    private static final String CONSUMER_NAME = "UserPointChangedConsumer";
    private static final List<String> COMPLETED_STATUSES = List.of(
            MessageProcessStatus.SUCCESS.name(),
            MessageProcessStatus.DUPLICATE.name()
    );

    private final PointHistoryRepository pointHistoryRepository;
    private final MessageProcessLogRepository messageProcessLogRepository;
    private final AccountPointApplyClient accountPointApplyClient;
    private final FailedMessagePublisher failedMessagePublisher;
    private final MessageProcessFailureRecorder messageProcessFailureRecorder;

    @Transactional
    public void processV2(UserPointChangedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        // [message_process_logs] RECEIVED INSERT
        MessageProcessLog processLog = messageProcessLogRepository.save(
                MessageProcessLog.builder()
                        .messageId(event.eventId())
                        .eventId(event.eventId())
                        .eventType(EventLogTypes.USER_POINT_CHANGED)
                        .consumerName(CONSUMER_NAME)
                        .queueName(RabbitMqKeys.UserPointChangedV2.QUEUE)
                        .processStatus(MessageProcessStatus.RECEIVED.name())
                        .receivedAt(now)
                        .duplicateYn("N")
                        .retryCount(0)
                        .traceId(event.eventId())
                        .build()
        );

        try {
            validatePayload(event);

            if (isDuplicateEvent(event.eventId())) {
                handleDuplicate(processLog, event);
                return;
            }

            // [message_process_logs] PROCESSING UPDATE
            processLog.markProcessing();
            messageProcessLogRepository.save(processLog);

            // [users] account internal API로 point_balance 반영
            accountPointApplyClient.apply(event);

            LocalDateTime processedAt = LocalDateTime.now();
            PointHistory pointHistory = PointHistory.builder()
                    .userId(event.userId())
                    .orderId(event.orderId())
                    .pointType(PointType.from(event.pointType()).name())
                    .pointAmount(event.changeAmount())
                    .balanceAfter(event.balanceAfter())
                    .processStatus(PointProcessStatus.SUCCESS.name())
                    .eventId(event.eventId())
                    .processedAt(processedAt)
                    .build();

            // [point_histories] SUCCESS INSERT
            pointHistoryRepository.save(pointHistory);
            markSuccess(processLog, event, processedAt);
        } catch (DataIntegrityViolationException exception) {
            handleDuplicate(processLog, event);
        } catch (UserPointChangedProcessException exception) {
            handleProcessException(processLog, event, exception);
        } catch (IllegalArgumentException exception) {
            handleBusinessFailure(
                    processLog,
                    event,
                    UserPointChangedProcessException.business(exception.getMessage())
            );
        } catch (RuntimeException exception) {
            UserPointChangedProcessException processException = UserPointChangedProcessException.system(
                    "UserPointChanged 처리 중 일시 오류가 발생했습니다.",
                    exception
            );
            messageProcessFailureRecorder.recordRetry(event, processException);
            throw processException;
        }
    }

    private boolean isDuplicateEvent(String eventId) {
        if (pointHistoryRepository.existsByEventId(eventId)) {
            return true;
        }
        return messageProcessLogRepository.existsByEventIdAndConsumerNameAndProcessStatusIn(
                eventId,
                CONSUMER_NAME,
                COMPLETED_STATUSES
        );
    }

    private void handleDuplicate(MessageProcessLog processLog, UserPointChangedEvent event) {
        log.warn(
                "중복 UserPointChanged 이벤트를 건너뜁니다. eventId={} userId={}",
                event.eventId(),
                event.userId()
        );

        // [message_process_logs] DUPLICATE UPDATE
        processLog.markDuplicate();
        messageProcessLogRepository.save(processLog);
    }

    private void handleProcessException(
            MessageProcessLog processLog,
            UserPointChangedEvent event,
            UserPointChangedProcessException exception
    ) {
        if (exception.isRetryable()) {
            messageProcessFailureRecorder.recordRetry(event, exception);
            throw exception;
        }

        handleBusinessFailure(processLog, event, exception);
    }

    private void handleBusinessFailure(
            MessageProcessLog processLog,
            UserPointChangedEvent event,
            UserPointChangedProcessException exception
    ) {
        // [message_process_logs] FAILED UPDATE
        processLog.markFailed(exception.getMessage());
        messageProcessLogRepository.save(processLog);

        failedMessagePublisher.publish(
                event,
                exception.getFailureType(),
                exception.getMessage(),
                processLog.getRetryCount()
        );

        log.warn(
                "복구 불가 UserPointChanged 이벤트를 failed-message ingest 큐로 전달했습니다. eventId={} failureType={} reason={}",
                event.eventId(),
                exception.getFailureType(),
                exception.getMessage()
        );
    }

    private void markSuccess(MessageProcessLog processLog, UserPointChangedEvent event, LocalDateTime processedAt) {
        // [message_process_logs] SUCCESS UPDATE
        LocalDateTime ackedAt = LocalDateTime.now();
        processLog.markSuccess(processedAt, ackedAt);
        messageProcessLogRepository.save(processLog);

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

    private void validatePayload(UserPointChangedEvent event) {
        if (event.userId() == null) {
            throw UserPointChangedProcessException.business("userId는 필수입니다.");
        }
        if (event.orderId() == null) {
            throw UserPointChangedProcessException.business("orderId는 필수입니다.");
        }
        if (event.pointType() == null || event.pointType().isBlank()) {
            throw UserPointChangedProcessException.business("pointType은 필수입니다.");
        }
        if (event.changeAmount() == null) {
            throw UserPointChangedProcessException.business("changeAmount는 필수입니다.");
        }
        if (event.balanceAfter() == null) {
            throw UserPointChangedProcessException.business("balanceAfter는 필수입니다.");
        }
        PointType.from(event.pointType());
    }
}
