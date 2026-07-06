package com.hopoong.processing.consumer.user.point.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.processing.consumer.user.point.exception.UserPointChangedProcessException;
import com.hopoong.processing.consumer.user.point.publisher.FailedMessagePublisher;
import com.hopoong.processing.consumer.user.point.service.MessageProcessFailureRecorder;
import lombok.RequiredArgsConstructor;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ProcessingRabbitListenerConfig {

    private final FailedMessagePublisher failedMessagePublisher;
    private final MessageProcessFailureRecorder messageProcessFailureRecorder;
    private final ObjectMapper objectMapper;

    @Value("${app.processing.max-retry-count:3}")
    private int maxRetryCount;

    @Bean
    public SimpleRabbitListenerContainerFactory processingRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    private Advice retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxRetryCount + 1)
                .recoverer((message, cause) -> {
                    UserPointChangedEvent event = readEvent(message);
                    UserPointChangedProcessException processException = toProcessException(cause);

                    messageProcessFailureRecorder.recordFailed(event, processException, maxRetryCount);
                    failedMessagePublisher.publish(
                            event,
                            processException.getFailureType(),
                            processException.getMessage(),
                            maxRetryCount
                    );
                })
                .build();
    }

    private UserPointChangedEvent readEvent(org.springframework.amqp.core.Message message) {
        try {
            return objectMapper.readValue(message.getBody(), UserPointChangedEvent.class);
        } catch (Exception exception) {
            throw new IllegalStateException("UserPointChangedEvent 메시지 파싱 실패", exception);
        }
    }

    private UserPointChangedProcessException toProcessException(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof UserPointChangedProcessException processException) {
                return processException;
            }
            current = current.getCause();
        }
        return UserPointChangedProcessException.system("UserPointChanged 처리 retry 초과", cause);
    }
}
