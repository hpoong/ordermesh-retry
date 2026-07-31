# 이벤트 발행 및 소비 처리 프로세스

## order-service

### 한눈에 보는 전체 흐름

```text
POST /api/outbox/user-point-changed
  -> event_logs에 READY로 저장 (Outbox)
  |
EventLogPublishScheduler (60초 주기)
  -> READY, RETRYING 조회 (nextRetryAt <= now)
  |
선점(claim): attemptCount++, 상태 RETRYING
  |
RabbitMQ 발행
  (exchange: user.events / routingKey: user.point.changed
   -> 큐: processing-service.user.point.changed.v2)
  |
발행 완료 -> PUBLISHED
```

### 실패 시

- 재시도 가능 (`attemptCount < 10`): `RETRYING` + `nextRetryAt` (30초 후)
- 재시도 불가 (10회 초과): `FAILED` (order-service Outbox 재시도 종료)

> 핵심: API는 MQ로 바로 보내지 않고 DB에만 적재한다. 스케줄러가 비동기로 발행한다.

## processing-service (소비 측 - Consumer)

`processing-service`에는 별도의 Outbox가 없다. RabbitMQ 큐에서 메시지를 받아 소비하고 처리 결과를 기록한다.

### 한눈에 보는 소비 흐름

```text
RabbitMQ 큐 수신
  (processing-service.user.point.changed.v2)
  |
UserPointChangedConsumer -> dispatch (v2 핸들러)
  |
message_process_logs RECEIVED 기록
  |
payload 검증 + eventId 중복 검사
  (중복이면 DUPLICATE -> account 호출 없이 종료)
  |
message_process_logs PROCESSING
  |
account-service API 호출
  (POST /internal/v1/users/point-changed -> pointBalance 반영)
  |
point_histories SUCCESS 저장
  |
message_process_logs SUCCESS -> RabbitMQ ack
```

> 핵심: processing-service는 잔액을 직접 수정하지 않고 account-service에 위임한다. `eventId`로 멱등 처리한다.

### 실패 처리 기준

| 실패 유형 | 대표 사례 | 처리 방법 | 큐 이동 |
| --- | --- | --- | --- |
| `BUSINESS` | 잘못된 payload, 검증 실패, 4xx 응답 | `FAILED` 기록 -> recovery ingest 전달 -> 재시도하지 않음 | main 큐 ack 후 ingest 발행<br>`exchange: user.events / routingKey: failed-messages.ingest`<br>`-> 큐: recovery-service.failed-messages.ingest` |
| `SYSTEM` / `TIMEOUT` | account-service 장애, 5xx, 네트워크 오류 | `RETRY` 기록 -> RabbitMQ 자동 재시도 | main 큐에서 재소비 (큐 이동 없음)<br>`큐: processing-service.user.point.changed.v2` |
| 재시도 소진 | 최대 3회까지 처리 실패 | `FAILED` 기록 -> recovery ingest 전달 | 1차: ingest 발행<br>`exchange: user.events / routingKey: failed-messages.ingest`<br>`-> 큐: recovery-service.failed-messages.ingest`<br>백업: main 큐 DLX -> DLQ<br>`큐: processing-service.user.point.changed.v2.dlq` |



## recovery-service (실패 적재/재처리)

`recovery-service`는 processing에서 최종 실패한 메시지를 `failed_messages`에 보관하고, 운영자가 필요할 때 원본 exchange/routing key로 다시 발행한다.

### 한눈에 보는 실패 ingest 흐름

```text
processing-service 처리 실패 확정
  |
FailedMessagePublisher
  |
RabbitMQ 발행
  (exchange: user.events / routingKey: failed-messages.ingest
   -> 큐: recovery-service.failed-messages.ingest)
  |
FailedMessageIngestConsumer 수신
  |
FailedMessageIngestService.ingest()
  |
(consumerName, eventId) 기준 기존 실패 건 조회
  |
신규면 failed_messages INSERT
기존이면 failureReason/retryCount/lastFailedAt/payload 갱신
  |
reprocess_status = WAITING
```

### DLQ 백업 적재 흐름

```text
processing-service.user.point.changed.v2.dlq 수신
  |
FailedMessageIngestConsumer.consumeUserPointChangedDlq()
  |
UserPointChangedEvent를 payload JSON으로 직렬화
  |
failed_messages에 SYSTEM 실패로 적재
  |
dlq_stored_yn = Y
reprocess_status = WAITING
```

> 핵심: recovery-service는 실패 메시지를 재처리 가능한 형태로 보관한다. 원본 `exchangeName`, `routingKey`, `payload`를 함께 저장해야 재발행할 수 있다.

### 한눈에 보는 수동 reprocess 흐름

```text
POST /internal/v1/failed-messages/{id}/reprocess
  |
FailedMessageReprocessService.reprocess(id)
  |
failed_messages 조회
  (id + reprocess_status = WAITING, pessimistic lock)
  |
PROCESSING으로 claim
  |
payload -> UserPointChangedEvent 역직렬화
  |
RabbitTemplate.convertAndSend(exchangeName, routingKey, event)
  |
재발행 성공 -> reprocess_status = SUCCESS, reprocessed_at 기록
재발행 실패 -> reprocess_status = FAILED, failure_reason 갱신
```

### 자동 reprocess 흐름

```text
FailedMessageReprocessScheduler
  (app.recovery.reprocess.enabled=true일 때만 동작)
  |
WAITING + failureType in (SYSTEM, TIMEOUT)
  |
lastFailedAt + minWaitMinutes 경과 건 조회
  |
batchSize만큼 FailedMessageReprocessService.reprocess(id) 호출
```

`BUSINESS` 실패는 payload나 데이터 자체가 잘못된 경우가 많으므로 자동 재처리 대상에서 제외한다.



### `failed_messages` 주요 상태

| 상태 | 의미 | 주요 전이 |
| --- | --- | --- |
| `WAITING` | 재처리 대기 | ingest 적재 또는 기존 실패 건 갱신 시 설정 |
| `PROCESSING` | 재처리 claim 완료 | 수동/자동 reprocess 시작 시 설정 |
| `SUCCESS` | 재발행 성공 | 원본 exchange/routing key로 발행 완료 |
| `FAILED` | 재발행 실패 | payload 역직렬화 실패 또는 MQ 발행 실패 |


## 참고: 큐 역할 정리

### 정상 수신/재시도

- 큐: `processing-service.user.point.changed.v2`

### 실패 ingest - recovery 적재

- exchange: `user.events`
- routingKey: `failed-messages.ingest`
- 큐: `recovery-service.failed-messages.ingest`

### DLQ - main 큐 DLX 백업

- 큐: `processing-service.user.point.changed.v2.dlq`
- ingest publish 실패 등으로 nack/reject 시 이동

## 서비스별 역할 비교

| 구분 | order-service | processing-service | recovery-service |
| --- | --- | --- | --- |
| 역할 | 이벤트 생성 + 발행 | 이벤트 소비 + 처리 | 실패 메시지 적재 + 재처리 |
| 패턴 | Outbox + Scheduler | RabbitMQ Consumer + Retry | Ingest Consumer + Reprocess API/Scheduler |
| 상태 테이블 | `event_logs` | `message_process_logs` / `point_histories` | `failed_messages` |
| 재시도 | 자체 Outbox (10회, 30초) | RabbitMQ listener (3회) + recovery 전달 | 원본 exchange/routing key 재발행 |
| 실패 종료 | `FAILED` (발행 포기) | `FAILED` + recovery-service ingest | `FAILED` (재발행 실패 기록) |
