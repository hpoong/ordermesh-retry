package com.hopoong.recovery.api.internal.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopoong.core.exception.CoreException;
import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.core.util.StringUtil;
import com.hopoong.recovery.enums.FailureType;
import com.hopoong.recovery.ingest.FailedMessageIngestCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FailedMessageCreateRequest(
        @NotBlank(message = "eventId는 필수입니다.")
        @Size(max = 100, message = "eventId는 100자 이하여야 합니다.")
        String eventId,

        @NotBlank(message = "consumerName은 필수입니다.")
        @Size(max = 100, message = "consumerName은 100자 이하여야 합니다.")
        String consumerName,

        @NotBlank(message = "queueName은 필수입니다.")
        @Size(max = 100, message = "queueName은 100자 이하여야 합니다.")
        String queueName,

        @NotBlank(message = "exchangeName은 필수입니다.")
        @Size(max = 100, message = "exchangeName은 100자 이하여야 합니다.")
        String exchangeName,

        @NotBlank(message = "routingKey는 필수입니다.")
        @Size(max = 100, message = "routingKey는 100자 이하여야 합니다.")
        String routingKey,

        @NotNull(message = "payload는 필수입니다.")
        JsonNode payload,

        @NotNull(message = "failureType은 필수입니다.")
        FailureType failureType,

        @NotBlank(message = "failureReason은 필수입니다.")
        String failureReason,

        @Min(value = 0, message = "retryCount는 0 이상이어야 합니다.")
        @Max(value = 1000, message = "retryCount는 1000 이하여야 합니다.")
        Integer retryCount,

        @Size(max = 1, message = "dlqStoredYn은 Y 또는 N이어야 합니다.")
        String dlqStoredYn
) {
    public FailedMessageCreateRequest {
        eventId = StringUtil.trimOrNull(eventId);
        consumerName = StringUtil.trimOrNull(consumerName);
        queueName = StringUtil.trimOrNull(queueName);
        exchangeName = StringUtil.trimOrNull(exchangeName);
        routingKey = StringUtil.trimOrNull(routingKey);
        failureReason = StringUtil.trimOrNull(failureReason);
        dlqStoredYn = StringUtil.trimOrNull(dlqStoredYn);
    }

    private static final CommonResponseCodeEnum RESPONSE_CODE = CommonResponseCodeEnum.RECOVERY_FAILED_MESSAGES;

    public FailedMessageIngestCommand toIngestCommand(ObjectMapper objectMapper) {
        return new FailedMessageIngestCommand(
                eventId,
                consumerName,
                queueName,
                exchangeName,
                routingKey,
                writePayload(objectMapper),
                failureType,
                failureReason,
                retryCount,
                dlqStoredYn
        );
    }

    private String writePayload(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw CoreException.badRequest(RESPONSE_CODE, "payload 형식이 올바르지 않습니다.");
        }
    }
}
