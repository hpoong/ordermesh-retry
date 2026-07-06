package com.hopoong.core.event;

import java.time.LocalDateTime;

/**
 * 주문 흐름에서 발생한 사용자 포인트 변경 이벤트.
 *
 * <p>order-service가 Outbox에 기록한 뒤 RabbitMQ로 발행하고,
 * processing-service가 수신해 중복 검사, account-service 잔액 반영,
 * point_histories 저장을 오케스트레이션한다.</p>
 *
 * @param eventId 이벤트 고유 ID. 중복 처리 방지 기준
 * @param eventType 이벤트 타입. USER_POINT_CHANGED
 * @param eventVersion 이벤트 스키마 버전. 현재 processing-service는 v2를 처리
 * @param userId 포인트 변경 대상 사용자 ID
 * @param orderId 포인트 변경과 연결된 주문 ID
 * @param pointType 포인트 유형. EARN, CANCEL, EXPIRE 중 하나
 * @param changeAmount 변경된 포인트 양
 * @param balanceAfter 변경 후 최종 포인트 잔액. account-service는 이 값을 최종 잔액으로 저장
 * @param occurredAt 도메인 이벤트 발생 시각
 */
public record UserPointChangedEvent(
        String eventId,
        String eventType,
        String eventVersion,
        Long userId,
        Long orderId,
        String pointType,
        Integer changeAmount,
        Integer balanceAfter,
        LocalDateTime occurredAt
) {
}
