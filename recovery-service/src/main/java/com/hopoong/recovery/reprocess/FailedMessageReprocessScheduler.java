package com.hopoong.recovery.reprocess;

import com.hopoong.recovery.entity.FailedMessage;
import com.hopoong.recovery.enums.FailureType;
import com.hopoong.recovery.enums.ReprocessStatus;
import com.hopoong.recovery.repository.FailedMessageRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedMessageReprocessScheduler {

    private static final List<FailureType> AUTO_REPROCESS_FAILURE_TYPES = List.of(
            FailureType.SYSTEM,
            FailureType.TIMEOUT
    );

    private final FailedMessageRepository failedMessageRepository;
    private final FailedMessageReprocessService failedMessageReprocessService;
    private final FailedMessageReprocessProperties properties;

    @Scheduled(fixedDelayString = "${app.recovery.reprocess.fixed-delay-ms:60000}")
    public void reprocessWaitingSystemFailures() {
        if (!properties.isEnabled()) {
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(properties.getMinWaitMinutes());
        List<FailedMessage> targets = failedMessageRepository
                .findByReprocessStatusAndFailureTypeInAndLastFailedAtBeforeOrderByLastFailedAtAscIdAsc(
                        ReprocessStatus.WAITING,
                        AUTO_REPROCESS_FAILURE_TYPES,
                        threshold,
                        PageRequest.of(0, Math.max(properties.getBatchSize(), 1))
                );

        for (FailedMessage target : targets) {
            try {
                FailedMessageReprocessResult result = failedMessageReprocessService.reprocess(target.getId());
                log.info(
                        "failed message auto reprocess result. id={}, eventId={}, status={}",
                        result.id(),
                        result.eventId(),
                        result.reprocessStatus()
                );
            } catch (RuntimeException e) {
                log.warn("failed message auto reprocess skipped. id={}, reason={}", target.getId(), e.getMessage());
            }
        }
    }
}
