package com.hopoong.order.scheduler;

import com.hopoong.order.publisher.EventLogPublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.event-outbox.publish", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EventLogPublishScheduler {

    private final EventLogPublishService eventLogPublishService;

    @Scheduled(fixedDelayString = "${app.event-outbox.publish.fixed-delay-ms:5000}")
    public void publishReadyEvents() {
        int publishedCount = eventLogPublishService.publishReadyEvents();
        if (publishedCount > 0) {
            log.info("Outbox 이벤트 {}건을 RabbitMQ로 발행했습니다.", publishedCount);
        }
    }
}
