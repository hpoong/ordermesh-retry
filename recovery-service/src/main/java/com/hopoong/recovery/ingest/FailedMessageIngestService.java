package com.hopoong.recovery.ingest;

import com.hopoong.recovery.config.RecoveryProperties;
import com.hopoong.recovery.entity.FailedMessage;
import com.hopoong.recovery.enums.ReprocessStatus;
import com.hopoong.recovery.repository.FailedMessageRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FailedMessageIngestService {

    private final FailedMessageRepository failedMessageRepository;
    private final RecoveryProperties recoveryProperties;

    @Transactional
    public FailedMessage ingest(FailedMessageIngestCommand command) {
        LocalDateTime now = LocalDateTime.now();
        return failedMessageRepository.findByConsumerNameAndEventId(command.consumerName(), command.eventId())
                .map(existing -> updateExisting(existing, command, now))
                .orElseGet(() -> createNew(command, now));
    }

    private FailedMessage updateExisting(FailedMessage failedMessage, FailedMessageIngestCommand command, LocalDateTime now) {
        failedMessage.updateFailure(
                command.failureReason(),
                normalizeRetryCount(command.retryCount()),
                now,
                command.failureType(),
                command.payload(),
                normalizeDlqStoredYn(command.dlqStoredYn())
        );
        return failedMessage;
    }

    private FailedMessage createNew(FailedMessageIngestCommand command, LocalDateTime now) {
        FailedMessage failedMessage = FailedMessage.builder()
                .eventId(command.eventId())
                .consumerName(command.consumerName())
                .queueName(command.queueName())
                .exchangeName(command.exchangeName())
                .routingKey(command.routingKey())
                .payload(command.payload())
                .failureType(command.failureType())
                .failureReason(command.failureReason())
                .retryCount(normalizeRetryCount(command.retryCount()))
                .maxRetryCount(recoveryProperties.getMaxRetryCount())
                .lastFailedAt(now)
                .dlqStoredYn(normalizeDlqStoredYn(command.dlqStoredYn()))
                .reprocessStatus(ReprocessStatus.WAITING)
                .build();

        return failedMessageRepository.save(failedMessage);
    }

    private Integer normalizeRetryCount(Integer retryCount) {
        return retryCount == null ? 0 : retryCount;
    }

    private String normalizeDlqStoredYn(String dlqStoredYn) {
        if ("Y".equalsIgnoreCase(dlqStoredYn)) {
            return "Y";
        }
        return "N";
    }
}
