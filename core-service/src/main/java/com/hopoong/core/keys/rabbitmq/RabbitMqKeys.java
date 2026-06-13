package com.hopoong.core.keys.rabbitmq;

public final class RabbitMqKeys {

    private RabbitMqKeys() { }


    public static final class UserPointChanged {

        // v1
        public static final String EXCHANGE = "user.events.v1";
        public static final String ROUTING_KEY = "user.point.changed";
        public static final String QUEUE = "account-service.user.point.changed.v1";
        public static final String DLQ = QUEUE + ".dlq";

        // v2
        public static final String PROCESSING_QUEUE = "processing-service.user.point.changed.v1";
        public static final String PROCESSING_DLQ = PROCESSING_QUEUE + ".dlq";

        private UserPointChanged() { }
    }
}
