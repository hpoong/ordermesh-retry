package com.hopoong.account.consumer.user.point.handler;

import com.hopoong.core.event.UserPointChangedEvent;

public interface UserPointChangedEventHandler {

    String supportedVersion();

    void handle(UserPointChangedEvent event);
}
