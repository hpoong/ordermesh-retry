package com.hopoong.order.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 슬로우 쿼리 판별 기준.
 * {@code app.slow-query} 접두사로 application.yml 값을 바인딩한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.slow-query")
public class OrderSlowQueryProperties {

    /** 슬로우 쿼리로 간주할 최소 실행 시간(밀리초) */
    private long thresholdMs = 1000;
}
