package com.hopoong.order.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Outbox에 적재된 이벤트를 RabbitMQ로 발행할 때 사용하는 스케줄러 설정.
 * {@code app.event-outbox.publish} 접두사로 application.yml 값을 바인딩한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.event-outbox.publish")
public class EventOutboxPublishProperties {

    /** Outbox 발행 스케줄러 활성화 여부 */
    private boolean enabled = true;

    /** 한 번의 폴링에서 발행을 시도할 최대 이벤트 수 */
    private int batchSize = 20;

    /** 이전 실행 종료 후 다음 폴링까지 대기 시간(밀리초) */
    private long fixedDelayMs = 1000 * 10;

    /** 발행 실패 시 최종 FAILED 처리까지 허용하는 최대 시도 횟수 */
    private int maxAttempts = 10;

    /** 발행 실패 후 다음 재시도까지 대기 시간(초) */
    private long retryDelaySeconds = 30;
}
