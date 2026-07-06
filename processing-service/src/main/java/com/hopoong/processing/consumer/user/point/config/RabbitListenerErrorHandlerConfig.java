package com.hopoong.processing.consumer.user.point.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.processing.consumer.user.point.exception.UnsupportedUserPointChangedVersionException;
import com.hopoong.processing.consumer.user.point.exception.UserPointChangedProcessException;
import com.hopoong.processing.consumer.user.point.publisher.FailedMessagePublisher;
import com.hopoong.processing.consumer.user.point.service.MessageProcessFailureRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Configuration
@RequiredArgsConstructor
public class RabbitListenerErrorHandlerConfig {

    private final FailedMessagePublisher failedMessagePublisher;
    private final MessageProcessFailureRecorder messageProcessFailureRecorder;
    private final ObjectMapper objectMapper;

    @Bean
    public RabbitListenerErrorHandler userPointChangedRabbitListenerErrorHandler() {
        return (amqpMessage, message, exception) -> {
            Throwable cause = unwrap(exception);

            if (isBusinessFailure(cause)) {
                UserPointChangedEvent event = extractEvent(amqpMessage, message);
                messageProcessFailureRecorder.recordFailed(event, cause, 0);
                failedMessagePublisher.publish(
                        event,
                        UserPointChangedProcessException.BUSINESS,
                        failureReason(cause),
                        0
                );
                return null;
            }

            throw exception;
        };
    }

    private boolean isBusinessFailure(Throwable cause) {
        if (cause instanceof UserPointChangedProcessException processException) {
            return !processException.isRetryable();
        }
        return cause instanceof UnsupportedUserPointChangedVersionException
                || cause instanceof IllegalArgumentException;
    }

    private UserPointChangedEvent extractEvent(
            org.springframework.amqp.core.Message amqpMessage,
            Message<?> message
    ) {
        if (message != null && message.getPayload() instanceof UserPointChangedEvent event) {
            return event;
        }
        try {
            return objectMapper.readValue(amqpMessage.getBody(), UserPointChangedEvent.class);
        } catch (Exception readException) {
            throw new IllegalStateException("UserPointChangedEvent 메시지 파싱 실패", readException);
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && !(current instanceof UserPointChangedProcessException)
                && !(current instanceof UnsupportedUserPointChangedVersionException)
                && !(current instanceof IllegalArgumentException)) {
            current = current.getCause();
        }
        return current;
    }

    private String failureReason(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
