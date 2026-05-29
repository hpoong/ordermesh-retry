package com.hopoong.core.keys.rabbitmq;

public final class RabbitMqKeys {

    private RabbitMqKeys() { }


    public static final class UserPointChanged {

        public static final String EXCHANGE = "user.events.v1";
        public static final String ROUTING_KEY = "user.point.changed";
        public static final String QUEUE = "account-service.user.point.changed.v1";
        public static final String DLQ = QUEUE + ".dlq";

        private UserPointChanged() { }
    }
}
