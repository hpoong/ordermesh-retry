package com.hopoong.core.keys.rabbitmq;

public final class RabbitMqKeys {

    private RabbitMqKeys() { }


    public static final class UserPointChanged {

        public static final String EXCHANGE = "user.events.v2";
        public static final String ROUTING_KEY = "user.point.changed.v2";
        public static final String QUEUE = "processing-service.user.point.changed.v2";
        public static final String DLQ = QUEUE + ".dlq";

        private UserPointChanged() { }
    }
}
