package com.hopoong.account.consumer.user.point.handler;

import com.hopoong.account.consumer.user.point.service.UserPointChangedProcessService;
import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.keys.event.EventVersions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPointChangedV1EventHandler implements UserPointChangedEventHandler {

    private final UserPointChangedProcessService userPointChangedProcessService;

    @Override
    public String supportedVersion() {
        return EventVersions.V1;
    }

    @Override
    public void handle(UserPointChangedEvent event) {
        userPointChangedProcessService.processV1(event);
    }
}
