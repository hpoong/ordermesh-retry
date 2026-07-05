package com.hopoong.recovery.api.internal.service;

import com.hopoong.core.exception.CoreException;
import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.recovery.api.internal.dto.FailedMessageResponse;
import com.hopoong.recovery.api.internal.dto.FailedMessageSearchQuery;
import com.hopoong.recovery.entity.FailedMessage;
import com.hopoong.recovery.ingest.FailedMessageIngestCommand;
import com.hopoong.recovery.ingest.FailedMessageIngestService;
import com.hopoong.recovery.repository.FailedMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class FailedMessageInternalService {

    private static final CommonResponseCodeEnum RESPONSE_CODE = CommonResponseCodeEnum.RECOVERY_FAILED_MESSAGES;

    private final FailedMessageRepository failedMessageRepository;
    private final FailedMessageIngestService failedMessageIngestService;

    // [실패 메시지 적재 — FailedMessageIngestService 위임, (consumer_name, event_id) UK 멱등 upsert]
    @Transactional
    public FailedMessageResponse createFailedMessage(FailedMessageIngestCommand command) {
        FailedMessage failedMessage = failedMessageIngestService.ingest(command);
        return FailedMessageResponse.from(failedMessage);
    }

    // [실패 메시지 목록 조회 — reprocess_status, failure_type 필터, last_failed_at 내림차순]
    @Transactional(readOnly = true)
    public List<FailedMessageResponse> getFailedMessages(FailedMessageSearchQuery query) {
        return failedMessageRepository.findAllByFilters(query.reprocessStatus(), query.failureType())
                .stream()
                .map(FailedMessageResponse::from)
                .toList();
    }

    // [실패 메시지 단건 조회 — 없으면 404]
    @Transactional(readOnly = true)
    public FailedMessageResponse getFailedMessage(Long id) {
        FailedMessage failedMessage = failedMessageRepository.findById(id)
                .orElseThrow(() -> CoreException.notFound(RESPONSE_CODE, "실패 메시지를 찾을 수 없습니다."));
        return FailedMessageResponse.from(failedMessage);
    }
}
