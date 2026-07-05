package com.hopoong.recovery.api.internal.dto;

import com.hopoong.core.exception.CoreException;
import com.hopoong.core.response.CommonResponseCodeEnum;
import com.hopoong.recovery.enums.FailureType;
import com.hopoong.recovery.enums.ReprocessStatus;

public record FailedMessageSearchQuery(
        ReprocessStatus reprocessStatus,
        FailureType failureType
) {

    private static final CommonResponseCodeEnum RESPONSE_CODE = CommonResponseCodeEnum.RECOVERY_FAILED_MESSAGES;

    public static FailedMessageSearchQuery from(String reprocessStatus, String failureType) {
        return new FailedMessageSearchQuery(
                parseReprocessStatus(reprocessStatus),
                parseFailureType(failureType)
        );
    }

    private static ReprocessStatus parseReprocessStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return ReprocessStatus.from(value.trim());
        } catch (IllegalArgumentException exception) {
            throw CoreException.badRequest(RESPONSE_CODE, "유효하지 않은 reprocess_status 입니다. (WAITING, PROCESSING, SUCCESS, FAILED)");
        }
    }

    private static FailureType parseFailureType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return FailureType.from(value.trim());
        } catch (IllegalArgumentException exception) {
            throw CoreException.badRequest(RESPONSE_CODE, "유효하지 않은 failure_type 입니다. (BUSINESS, SYSTEM, TIMEOUT)");
        }
    }
}
