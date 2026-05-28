package com.hopoong.order.api.user;

import com.hopoong.order.entity.EventLog;
import com.hopoong.order.outbox.UserPointChangedOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api/outbox")
public class UserPointChangedOutboxController {

    private final UserPointChangedOutboxService userPointChangedOutboxService;

    @PostMapping("/user-point-changed")
    public ResponseEntity<EventLog> record(@RequestBody UserPointChangedOutboxRequest request) {
        EventLog saved = userPointChangedOutboxService.record(
                request.userId(),
                request.changeAmount(),
                request.balanceAfter(),
                request.occurredAt()
        );
        return ResponseEntity.ok(saved);
    }
}
