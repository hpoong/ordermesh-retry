package com.hopoong.processing.consumer.user.point.listener;

import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.keys.event.EventLogTypes;
import com.hopoong.processing.consumer.user.point.exception.UnsupportedUserPointChangedVersionException;
import com.hopoong.processing.consumer.user.point.handler.UserPointChangedEventHandler;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserPointChangedEventDispatcher {

    private final Map<String, UserPointChangedEventHandler> handlerByVersion;

    public UserPointChangedEventDispatcher(List<UserPointChangedEventHandler> handlers) {
        this.handlerByVersion = handlers.stream()
                .collect(Collectors.toMap(
                        UserPointChangedEventHandler::supportedVersion,
                        Function.identity()
                ));
    }

    public void dispatch(UserPointChangedEvent event) {
        validateEventType(event);

        UserPointChangedEventHandler handler = handlerByVersion.get(event.eventVersion());
        if (handler == null) {
            throw new UnsupportedUserPointChangedVersionException(event.eventVersion());
        }

        handler.handle(event);
    }

    private void validateEventType(UserPointChangedEvent event) {
        if (!EventLogTypes.USER_POINT_CHANGED.equals(event.eventType())) {
            throw new IllegalArgumentException(
                    "지원하지 않는 이벤트 타입입니다. eventType=" + event.eventType()
            );
        }
    }
}
