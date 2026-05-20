package com.hopoong.order.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.keys.event.EventLogTypes;
import com.hopoong.core.keys.event.EventVersions;
import com.hopoong.core.keys.rabbitmq.RabbitMqKeys;
import com.hopoong.order.entity.EventLog;
import com.hopoong.order.enums.EventPublishStatus;
import com.hopoong.order.repository.EventLogRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPointChangedOutboxService {

    private final EventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public EventLog record(
            Long userId,
            BigDecimal changeAmount,
            BigDecimal balanceAfter,
            LocalDateTime occurredAt
    ) {
        String eventId = UUID.randomUUID().toString();
        UserPointChangedEvent event = new UserPointChangedEvent(
                eventId,
                EventLogTypes.USER_POINT_CHANGED,
                EventVersions.V1,
                userId,
                changeAmount,
                balanceAfter,
                occurredAt
        );

        EventLog eventLog = EventLog.builder()
                .eventId(eventId)
                .eventType(EventLogTypes.USER_POINT_CHANGED)
                .eventVersion(EventVersions.V1)
                .routingKey(RabbitMqKeys.UserPointChanged.ROUTING_KEY)
                .exchangeName(RabbitMqKeys.UserPointChanged.EXCHANGE)
                .payload(serialize(event))
                .publishStatus(EventPublishStatus.READY)
                .publishAttemptCount(0)
                .occurredAt(occurredAt)
                .build();

        return eventLogRepository.save(eventLog);
    }

    private String serialize(UserPointChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("사용자 포인트 변경 이벤트 직렬화에 실패했습니다.", exception);
        }
    }
}
