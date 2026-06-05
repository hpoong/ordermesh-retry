package com.hopoong.account.consumer.user.point;

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

    private final UserPointChangedEventDispatcher userPointChangedEventDispatcher;

    @RabbitListener(queues = RabbitMqKeys.UserPointChanged.QUEUE)
    public void consumeUserPointChanged(UserPointChangedEvent message) {
        log.info(
                "UserPointChanged 이벤트 수신. eventId={} eventVersion={} userId={}",
                message.eventId(),
                message.eventVersion(),
                message.userId()
        );
        userPointChangedEventDispatcher.dispatch(message);
    }
}