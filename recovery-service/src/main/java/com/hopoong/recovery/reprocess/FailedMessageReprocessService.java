package com.hopoong.recovery.reprocess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.exception.CoreException;
import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.recovery.entity.FailedMessage;
import com.hopoong.recovery.enums.ReprocessStatus;
import com.hopoong.recovery.repository.FailedMessageRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class FailedMessageReprocessService {

    private static final CommonResponseCodeEnum RESPONSE_CODE = CommonResponseCodeEnum.RECOVERY_FAILED_MESSAGES;

    private final FailedMessageRepository failedMessageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public FailedMessageReprocessResult reprocess(Long id) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> reprocessInTransaction(id)));
    }

    public List<FailedMessageReprocessResult> reprocessAll(List<Long> ids) {
        return ids.stream()
                .map(this::reprocess)
                .toList();
    }

    private FailedMessageReprocessResult reprocessInTransaction(Long id) {
        FailedMessage failedMessage = failedMessageRepository.findByIdAndReprocessStatus(id, ReprocessStatus.WAITING)
                .orElseThrow(() -> CoreException.unprocessable(RESPONSE_CODE, "재처리 대기 상태의 실패 메시지를 찾을 수 없습니다."));

        failedMessage.claimForReprocess();

        try {
            UserPointChangedEvent event = objectMapper.readValue(failedMessage.getPayload(), UserPointChangedEvent.class);
            rabbitTemplate.convertAndSend(failedMessage.getExchangeName(), failedMessage.getRoutingKey(), event);
            failedMessage.markReprocessSuccess(LocalDateTime.now());
        } catch (JsonProcessingException e) {
            failedMessage.markReprocessFailed("재처리 payload 역직렬화 실패: " + e.getOriginalMessage(), LocalDateTime.now());
        } catch (RuntimeException e) {
            failedMessage.markReprocessFailed("재처리 MQ 재발행 실패: " + e.getMessage(), LocalDateTime.now());
        }

        return FailedMessageReprocessResult.from(failedMessage);
    }
}
