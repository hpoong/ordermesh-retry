package com.hopoong.core.keys.rabbitmq;

public final class RabbitMqKeys {

    private RabbitMqKeys() { }

    public static final class UserPointChangedV2 {

        public static final String EXCHANGE = "user.events";
        public static final String ROUTING_KEY = "user.point.changed";
        public static final String QUEUE = "processing-service.user.point.changed.v2";
        public static final String DLQ = QUEUE + ".dlq";

        private UserPointChangedV2() { }
    }
}
