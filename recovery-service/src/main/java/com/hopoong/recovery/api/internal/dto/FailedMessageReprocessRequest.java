package com.hopoong.recovery.api.internal.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record FailedMessageReprocessRequest(
        @NotEmpty(message = "재처리할 실패 메시지 ID 목록은 필수입니다.")
        List<Long> ids
) {
}
