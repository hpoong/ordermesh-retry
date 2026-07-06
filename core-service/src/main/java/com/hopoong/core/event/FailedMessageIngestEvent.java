package com.hopoong.core.event;

/**
 * 처리 실패 메시지를 recovery-service ingest 큐로 전달하기 위한 MQ 이벤트.
 *
 * @param eventId 실패한 원본 이벤트 ID
 * @param consumerName 실패를 발생시킨 consumer 이름
 * @param queueName 원본 메시지를 수신한 queue 이름
 * @param exchangeName 원본 메시지가 발행된 exchange 이름
 * @param routingKey 원본 메시지 routing key
 * @param payload 원본 이벤트 payload JSON
 * @param failureType 실패 유형. BUSINESS, SYSTEM, TIMEOUT 중 하나
 * @param failureReason 실패 상세 사유
 * @param retryCount processing-service에서 누적된 재시도 횟수
 */
public record FailedMessageIngestEvent(
        String eventId,
        String consumerName,
        String queueName,
        String exchangeName,
        String routingKey,
        String payload,
        String failureType,
        String failureReason,
        Integer retryCount
) {
}
