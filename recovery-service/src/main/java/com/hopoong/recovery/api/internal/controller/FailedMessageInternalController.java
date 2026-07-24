package com.hopoong.recovery.api.internal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.core.response.SuccessResponse;
import com.hopoong.recovery.api.internal.dto.FailedMessageCreateRequest;
import com.hopoong.recovery.api.internal.dto.FailedMessageReprocessRequest;
import com.hopoong.recovery.api.internal.dto.FailedMessageSearchQuery;
import com.hopoong.recovery.api.internal.service.FailedMessageInternalService;
import com.hopoong.recovery.reprocess.FailedMessageReprocessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/failed-messages")
public class FailedMessageInternalController {

    private static final CommonResponseCodeEnum RESPONSE_CODE = CommonResponseCodeEnum.RECOVERY_FAILED_MESSAGES;

    private final FailedMessageInternalService failedMessageInternalService;
    private final FailedMessageReprocessService failedMessageReprocessService;
    private final ObjectMapper objectMapper;

    // [실패 메시지 수동 적재 — E2E·운영용, (consumer_name, event_id) UK 멱등 upsert]
    @PostMapping
    public SuccessResponse createFailedMessage(@Valid @RequestBody FailedMessageCreateRequest request) {
        return new SuccessResponse(
                RESPONSE_CODE,
                failedMessageInternalService.createFailedMessage(request.toIngestCommand(objectMapper))
        );
    }

    // [실패 메시지 목록 조회 — reprocess_status, failure_type 쿼리 필터(선택)]
    @GetMapping
    public SuccessResponse getFailedMessages(
            @RequestParam(name = "reprocess_status", required = false) String reprocessStatus,
            @RequestParam(name = "failure_type", required = false) String failureType
    ) {
        FailedMessageSearchQuery query = FailedMessageSearchQuery.from(reprocessStatus, failureType);
        return new SuccessResponse(RESPONSE_CODE, failedMessageInternalService.getFailedMessages(query));
    }

    // [실패 메시지 단건 상세 조회]
    @GetMapping("/{id}")
    public SuccessResponse getFailedMessage(@PathVariable Long id) {
        return new SuccessResponse(RESPONSE_CODE, failedMessageInternalService.getFailedMessage(id));
    }

    // [실패 메시지 단건 재처리 — 원본 exchange/routing_key로 재발행]
    @PostMapping("/{id}/reprocess")
    public SuccessResponse reprocessFailedMessage(@PathVariable Long id) {
        return new SuccessResponse(RESPONSE_CODE, failedMessageReprocessService.reprocess(id));
    }

    // [실패 메시지 일괄 재처리 — 운영·E2E 편의용]
    @PostMapping("/reprocess")
    public SuccessResponse reprocessFailedMessages(@Valid @RequestBody FailedMessageReprocessRequest request) {
        return new SuccessResponse(RESPONSE_CODE, failedMessageReprocessService.reprocessAll(request.ids()));
    }
}
