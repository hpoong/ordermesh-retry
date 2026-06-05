package com.hopoong.order.api.user;

import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.core.response.SuccessResponse;
import com.hopoong.order.entity.EventLog;
import com.hopoong.order.outbox.UserPointChangedOutboxService;
import lombok.RequiredArgsConstructor;
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

    private static final CommonResponseCodeEnum RESPONSE_CODE = CommonResponseCodeEnum.ORDER_POINT;
    private final UserPointChangedOutboxService userPointChangedOutboxService;

    // [상품 구매 확정 후 포인트 변경 처리]
    @PostMapping("/user-point-changed")
    public SuccessResponse record(@RequestBody UserPointChangedOutboxRequest request) {
        EventLog saved = userPointChangedOutboxService.record(
                request.userId(),
                request.orderId(),
                request.pointType(),
                request.changeAmount(),
                request.balanceAfter(),
                request.occurredAt()
        );
        return new SuccessResponse(RESPONSE_CODE, saved);
    }
}
