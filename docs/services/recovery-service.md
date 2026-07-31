# recovery-service 프로세스

## 1. 서비스 역할

`recovery-service`는 processing 소비 실패를 영구 보관하고, 운영자가 원본 MQ로 재처리할 수 있게 하는 서비스입니다.

주요 역할은 다음과 같습니다.

- `failed_messages` 테이블 소유 (INSERT/UPDATE)
- ingest 큐에서 실패 메시지 수신·적재
- main DLQ 백업 메시지 적재
- `(consumer_name, event_id)` UK 기준 멱등 upsert
- 실패 메시지 조회·수동 적재 Internal API
- 원본 exchange/routing key로 재발행(reprocess)

`recovery-service`는 order Outbox 발행 실패를 다루지 않습니다. `event_logs` 재시도는 order-service 책임입니다.

processing이 recovery를 **HTTP로 호출하지 않습니다.** 런타임 실패 적재는 MQ ingest 큐(및 DLQ 백업)만 사용합니다.

로컬 기본 포트는 `9300`입니다.

## 2. 전체 흐름

```text
processing 실패 확정
  -> FailedMessagePublisher
     -> recovery-service.failed-messages.ingest
        -> FailedMessageIngestConsumer
           -> FailedMessageIngestService.ingest()
              -> failed_messages (reprocess_status = WAITING)

운영 재처리
  -> POST /internal/v1/failed-messages/{id}/reprocess
     -> FailedMessageReprocessService
        -> 원본 exchange/routing_key 재발행
           -> processing-service.user.point.changed.v2
```

DLX로 main DLQ에 쌓인 메시지도 동일 ingest 서비스로 적재합니다 (`dlq_stored_yn = Y`).

## 3. 메시지 수신 프로세스 (Ingest)

`FailedMessageIngestConsumer`가 RabbitMQ 큐를 구독합니다.

### 3.1 Ingest 큐 (권장 경로)

```text
recovery-service.failed-messages.ingest
```

| 항목 | 값 |
| --- | --- |
| Exchange | `user.events` |
| Routing Key | `failed-messages.ingest` |
| Queue | `recovery-service.failed-messages.ingest` |
| 메시지 | `FailedMessageIngestEvent` |

처리 흐름은 다음과 같습니다.

```text
1. FailedMessageIngestEvent 수신
2. FailedMessageIngestCommand로 변환 (dlq_stored_yn = N)
3. FailedMessageIngestService.ingest() 호출
4. (consumer_name, event_id) UK 기준 upsert
5. ack
```

### 3.2 Main DLQ (백업 경로)

```text
processing-service.user.point.changed.v2.dlq
```

처리 흐름은 다음과 같습니다.

```text
1. UserPointChangedEvent 수신
2. payload JSON 직렬화
3. FailedMessageIngestService.ingest() 호출
   - consumerName = UserPointChangedConsumer
   - failureType = SYSTEM
   - dlq_stored_yn = Y
4. ack
```

권장 종료 경로는 ingest publish 단일화이고, DLQ Consumer는 백업입니다.

## 4. Ingest 서비스

실제 적재는 `FailedMessageIngestService`에서 진행합니다.

MQ Consumer와 수동 적재 API가 이 서비스를 공통으로 사용합니다.

```text
1. (consumer_name, event_id)로 기존 행 조회
2. 없으면 INSERT
   - reprocess_status = WAITING
   - max_retry_count = app.recovery.max-retry-count (기본 3)
3. 있으면 updateFailure()로 갱신
   - failure_reason, retry_count, last_failed_at, failure_type, payload, dlq_stored_yn
   - reprocess_status = WAITING 복귀
   - reprocessed_at = null
```

동일 키로 다시 들어오면 UK 위반 없이 한 행만 유지됩니다.

## 5. Internal API

운영·E2E용 HTTP API입니다. 런타임 실패 적재 경로가 아닙니다.

기본 prefix: `/internal/v1/failed-messages`  
응답 코드: `CommonResponseCodeEnum.RECOVERY_FAILED_MESSAGES`

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/internal/v1/failed-messages` | 수동 적재 (E2E·운영) |
| `GET` | `/internal/v1/failed-messages` | 목록 (`reprocess_status`, `failure_type` 필터) |
| `GET` | `/internal/v1/failed-messages/{id}` | 상세 조회 |
| `POST` | `/internal/v1/failed-messages/{id}/reprocess` | 단건 재처리 |
| `POST` | `/internal/v1/failed-messages/reprocess` | 복수 ID 일괄 재처리 |

목록은 `last_failed_at` 내림차순으로 조회합니다. 단건 조회 시 없으면 404입니다.

## 6. 재처리(reprocess)

`FailedMessageReprocessService`가 `WAITING` 건을 claim한 뒤 원본 MQ로 재발행합니다.

```text
1. id + reprocess_status = WAITING 조회 (비관적 lock)
2. PROCESSING으로 claim
3. payload를 UserPointChangedEvent로 역직렬화
4. RabbitTemplate.convertAndSend(exchangeName, routingKey, event)
5. 성공 → SUCCESS + reprocessed_at
6. 실패 → FAILED + failure_reason 갱신
   - payload 역직렬화 실패
   - MQ 재발행 실패
```

재발행 대상은 ingest 시 저장된 `exchange_name` + `routing_key`입니다. 보통 main 큐 `processing-service.user.point.changed.v2`로 다시 들어갑니다.

processing의 `eventId` 중복 검사가 있으므로, 이미 `SUCCESS`/`DUPLICATE`인 이벤트는 안전하게 skip됩니다.

`WAITING`이 아니면 422(`unprocessable`)로 거절합니다.

### 자동 Scheduler

`FailedMessageReprocessScheduler`는 기본 비활성입니다.

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `app.recovery.reprocess.enabled` | `false` | 자동 재처리 on/off |
| `app.recovery.reprocess.fixed-delay-ms` | `60000` | 스케줄 주기 |
| `app.recovery.reprocess.min-wait-minutes` | `10` | `last_failed_at` 이후 대기 |
| `app.recovery.reprocess.batch-size` | `20` | 한 번에 처리할 건수 |

대상 조건:

| 조건 | 내용 |
| --- | --- |
| 상태 | `WAITING` |
| 유형 | `SYSTEM`, `TIMEOUT` |
| 제외 | `BUSINESS` (수동만) |

`BUSINESS`는 payload·데이터 오류 재발행 방지를 위해 자동 대상에서 제외합니다.

## 7. 실패 유형·재처리 상태

### FailureType

| 값 | 설명 | 자동 reprocess |
| --- | --- | --- |
| `BUSINESS` | 복구 불가 (4xx, payload, 미지원 버전 등) | 수동만 |
| `SYSTEM` | 일시 시스템 오류 (5xx 등) | Scheduler 대상(선택) |
| `TIMEOUT` | 타임아웃/네트워크 | Scheduler 대상(선택) |

### ReprocessStatus

| 값 | 설명 |
| --- | --- |
| `WAITING` | 재처리 대기 |
| `PROCESSING` | 재처리 진행 중 (claim) |
| `SUCCESS` | 재발행 성공 |
| `FAILED` | 재발행 실패 |

## 8. 데이터 저장

`recovery-service`가 소유하는 테이블은 다음과 같습니다.

| 테이블 | 설명 |
| --- | --- |
| `failed_messages` | 실패 메시지 영구 보관·재처리 상태 |

주요 필드:

| 필드 | 설명 |
| --- | --- |
| `event_id` | 실패한 이벤트 ID |
| `consumer_name` | 실패 Consumer 이름 |
| `queue_name` / `exchange_name` / `routing_key` | 원본 MQ 메타 |
| `payload` | 원본 메시지 JSON (JSONB) |
| `failure_type` | `BUSINESS` / `SYSTEM` / `TIMEOUT` |
| `failure_reason` | 실패 상세 |
| `retry_count` / `max_retry_count` | 재시도 횟수 |
| `dlq_stored_yn` | DLQ 경유 여부 (`Y`/`N`) |
| `reprocess_status` | 재처리 상태 |
| `reprocessed_at` | 재처리 완료 시각 |

멱등 키는 `(consumer_name, event_id)` UK입니다.

processing은 이 테이블에 직접 INSERT하지 않습니다. MQ publish만 수행합니다.

## 9. 정리

`recovery-service`는 processing 실패의 최종 저장소이자 재처리 허브입니다.

실패는 MQ ingest로만 들어오고, 운영 조회·재처리는 Internal API로 수행합니다. 핵심은 무한 requeue 없이 실패를 남기고, 원인 해소 후 안전하게 다시 processing으로 되돌리는 것입니다.
