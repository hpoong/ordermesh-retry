package com.hopoong.processing.consumer.user.point.handler;

import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.keys.event.EventVersions;
import com.hopoong.processing.consumer.user.point.service.UserPointChangedProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPointChangedV2EventHandler implements UserPointChangedEventHandler {

    private final UserPointChangedProcessService userPointChangedProcessService;

    @Override
    public String supportedVersion() {
        return EventVersions.V2;
    }

    @Override
    public void handle(UserPointChangedEvent event) {
        userPointChangedProcessService.processV2(event);
    }
}
