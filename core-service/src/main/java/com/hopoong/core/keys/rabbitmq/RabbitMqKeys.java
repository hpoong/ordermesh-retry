package com.hopoong.core.keys.rabbitmq;

public final class RabbitMqKeys {

    private RabbitMqKeys() { }


    // 사용자 포인트 변경
    public static final class UserPointChanged {

        public static final String EXCHANGE = "user.point.events.v1";
        public static final String ROUTING_KEY = "user.point.changed";
        public static final String QUEUE = "core.user-point.changed.v1";
        public static final String DLQ = QUEUE + ".dlq";

        private UserPointChanged() { }
    }
}
