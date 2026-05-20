package com.hopoong.order.publisher;

import com.hopoong.order.entity.EventLog;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPointChangedEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    // [메시지 발행]
    public void publish(EventLog eventLog) {
        Message message = MessageBuilder.withBody(eventLog.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventLog.getEventId())
                .build();

        rabbitTemplate.send(eventLog.getExchangeName(), eventLog.getRoutingKey(), message);
    }
}
