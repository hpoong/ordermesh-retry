package com.hopoong.account.consumer;

import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.keys.rabbitmq.RabbitMqKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserPointChangedConsumer {

    @RabbitListener(queues = RabbitMqKeys.UserPointChanged.QUEUE)
    public void consumeUserPointChanged(UserPointChangedEvent message) {
        log.info("User point changed event received. message={}", message);
    }

}