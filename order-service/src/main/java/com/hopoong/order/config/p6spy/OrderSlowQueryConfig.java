package com.hopoong.order.config.p6spy;

import com.hopoong.order.config.properties.OrderSlowQueryProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderSlowQueryConfig {

    public OrderSlowQueryConfig(OrderSlowQueryProperties properties) {
        OrderP6spyPrettySqlFormatter.setSlowQueryThresholdMs(properties.getThresholdMs());
    }
}
