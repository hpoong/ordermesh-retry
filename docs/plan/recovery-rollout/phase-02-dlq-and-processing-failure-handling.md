# Phase 2: MQ 실패 전달 + processing 실패 분기 + Ingest Consumer

> **선행:** [Phase 1](phase-01-recovery-bootstrap-and-ingest-api.md)  
> **후행:** [Phase 3](phase-03-reprocess.md)  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 5.5, 8.2, [exception-handling-convention](../../convention/exception-handling-convention.md)

---

## 목적

processing 소비 실패를 **유형별로 분기**하고, 모든 종료 실패를 **MQ ingest 큐**로 recovery에 전달한다.

recovery-service `FailedMessageIngestConsumer`가 ingest 큐(및 DLQ 백업)를 소비해 `failed_messages`에 적재한다.

**processing → recovery HTTP 호출은 사용하지 않는다.**

---

## MQ 계약

| 상수 | 값 | 소유 |
|------|-----|------|
| Main Queue | `processing-service.user.point.changed.v2` | processing |
| Ingest Queue | `recovery-service.failed-messages.ingest` | recovery |
| Main DLQ | `processing-service.user.point.changed.v2.dlq` | processing (DLX 백업) |
| Ingest 메시지 | `FailedMessageIngestEvent` | core 또는 processing DTO |

`FailedMessageIngestEvent` 필드 (제안):

| 필드 | 설명 |
|------|------|
| `eventId` | 이벤트 ID |
| `consumerName` | Consumer 이름 |
| `queueName` | 원본 수신 큐 |
| `exchangeName` | 원본 exchange |
| `routingKey` | 원본 routing key |
| `payload` | 원본 `UserPointChangedEvent` JSON |
| `failureType` | `BUSINESS` / `SYSTEM` / `TIMEOUT` |
| `failureReason` | 실패 상세 |
| `retryCount` | 누적 재시도 횟수 |

---

## 전제조건

- [ ] Phase 1: `FailedMessageIngestService`·조회 API 기동 검증 완료
- [ ] Phase 2 착수 전 **retry 메커니즘** 확정 (Spring AMQP RetryInterceptor 권장)
- [ ] RabbitMQ docker-compose 기동 가능

---

## 시나리오 정책 (이 Phase 범위)

### S2. 복구 불가 — ingest 큐 즉시 publish

| 조건 | failure_type | message_process_logs | main MQ | recovery |
|------|--------------|----------------------|---------|----------|
| 필수값 누락 / 잘못된 pointType | `BUSINESS` | `FAILED` | publish 후 **ack** | ingest Consumer |
| 미지원 eventVersion | `BUSINESS` | `FAILED` | publish 후 ack | ingest Consumer |
| 잘못된 eventType | `BUSINESS` | `FAILED` | publish 후 ack | ingest Consumer |
| account 4xx | `BUSINESS` | `FAILED` | publish 후 ack | ingest Consumer |

**핵심:** 예외 rethrow 대신 **실패 기록 → ingest 큐 publish → main ack**. **retry 금지**.

### S3. 일시 오류 — retry 후 ingest 큐 publish

| 조건 | failure_type | 1~N회 | retry 초과 |
|------|--------------|-------|------------|
| account 5xx | `SYSTEM` | `RETRY`, requeue | ingest 큐 publish |
| account 타임아웃/네트워크 | `TIMEOUT` | 동일 | ingest 큐 publish |
| point_histories INSERT 실패 (account 성공 후) | `SYSTEM` | 동일 (멱등 안전) | ingest 큐 publish |

**retry_count 정책:**

- `message_process_logs.retry_count` — processing 처리 단위 추적
- `failed_messages.retry_count` — ingest 적재 시점 누적
- `max_retry_count` 기본값 3 (`application.yml` 오버라이드 가능)

### S4. ingest 큐 / DLQ → failed_messages 동기화

`FailedMessageIngestConsumer` 수신 시:

1. `FailedMessageIngestEvent` 파싱
2. `FailedMessageIngestService.ingest()` 호출
3. `(consumer_name, event_id)` UK 기준 upsert
4. DLQ 경유 시 `dlq_stored_yn = Y`, ingest 직행 시 `N` (또는 출처 헤더로 구분)
5. 메시지 ack

---

## 작업 목록

### 1. core-service — MQ Bean

**파일:** `core-service/src/main/java/com/hopoong/core/keys/rabbitmq/RabbitMqKeys.java`

| 상수 | 값 |
|------|-----|
| `INGEST_QUEUE` | `recovery-service.failed-messages.ingest` |
| `DLQ` | `processing-service.user.point.changed.v2.dlq` (기존) |

**파일:** `core-service/src/main/java/com/hopoong/core/config/RabbitMqConfig.java`

| Bean | 설명 |
|------|------|
| `failedMessageIngestQueue()` | `recovery-service.failed-messages.ingest` (durable) |
| `userPointChangedDlq()` | main DLQ (durable) |
| `userPointChangedQueue()` 수정 | `x-dead-letter-exchange`, `x-dead-letter-routing-key` (DLX 백업) |
| binding | ingest 큐·DLQ binding |

### 2. processing-service — `FailedMessagePublisher`

**파일:** `processing-service/.../publisher/FailedMessagePublisher.java`

- `RabbitTemplate.convertAndSend` → `recovery-service.failed-messages.ingest`
- body: `FailedMessageIngestEvent`
- **publisher confirm** 또는 send 실패 시 정책 확정 (권장: confirm 실패 시 ack 금지·로그)

**금지:** `RecoveryFailedMessageClient` (HTTP) — **구현하지 않음**

### 3. processing-service — 예외 분류 보강

**파일:** `AccountPointApplyClient.java`

- 4xx 응답 시 HTTP status code를 예외에 포함
- 5xx / `RestClientException` → retry 대상 예외로 분류

**파일:** `UserPointChangedProcessService.java`

| 변경 | 내용 |
|------|------|
| `RETRY` 상태 전이 | 일시 오류 시 `markRetry()` |
| `FAILED` 상태 전이 | 복구 불가·종료 실패 시 |
| `retry_count` 증가 | 재시도마다 increment |
| 복구 불가 catch | `FailedMessagePublisher.publish()` 후 **예외 미전파** (ack) |

**파일:** `MessageProcessLog.java`

- `markRetry()`, `markFailed()` 보강

### 4. processing-service — `RabbitListenerErrorHandler`

**파일:** `processing-service/.../config/RabbitListenerErrorHandlerConfig.java` (신규)

| 예외 유형 | 정책 |
|-----------|------|
| 멱등·중복 (return 정상 종료) | ack |
| `BUSINESS` (복구 불가) | ingest publish + ack |
| `SYSTEM` / `TIMEOUT` | retry → 초과 시 ingest publish + ack |
| `UnsupportedUserPointChangedVersionException` | `BUSINESS` → ingest publish + ack |
| `IllegalArgumentException` | `BUSINESS` → ingest publish + ack |

Spring AMQP `RetryInterceptor`와 연계. retry 소진 시 `FailedMessagePublisher` 호출.

### 5. recovery-service — `FailedMessageIngestConsumer`

**파일:** `recovery-service/.../consumer/FailedMessageIngestConsumer.java`

```java
@RabbitListener(queues = RabbitMqKeys.FailedMessageIngest.QUEUE)
public void consumeIngest(FailedMessageIngestEvent event) { ... }
```

- Phase 1 `FailedMessageIngestService` 재사용

**(선택) DLQ 백업 Consumer**

```java
@RabbitListener(queues = RabbitMqKeys.UserPointChangedV2.DLQ)
public void consumeDlq(...) { ... }  // 동일 ingest 서비스, dlq_stored_yn = Y
```

DLX만으로 종료하는 경우 백업. **권장 종료 경로는 ingest publish 단일화.**

### 6. 트랜잭션 경계

- `message_process_logs` FAILED/RETRY 기록과 ingest publish 순서 확정
- 권장: DB 커밋 후 publish, 또는 `REQUIRES_NEW`로 로그 유지 후 publish

---

## 연동 방식 (MQ 단일 경로)

| 실패 유형 | processing 동작 | failed_messages 경로 |
|-----------|-----------------|----------------------|
| 복구 불가 (4xx, payload, 미지원 버전) | ingest **publish** → main ack | Ingest Consumer |
| 일시 오류 (5xx, 네트워크) | retry → 초과 시 ingest **publish** | Ingest Consumer |
| DLX 백업 | (자동) | DLQ Consumer → 동일 ingest 서비스 |

---

## 파일 변경 요약

| 구분 | 범위 |
|------|------|
| **신규** | `FailedMessageIngestEvent`, `FailedMessagePublisher`, ErrorHandler config |
| **신규** | `recovery-service/.../consumer/FailedMessageIngestConsumer` |
| **수정** | `core-service/RabbitMqKeys`, `RabbitMqConfig` |
| **수정** | `processing-service/UserPointChangedProcessService`, `MessageProcessLog`, `AccountPointApplyClient` |
| **수정** | `recovery-service/application.yml` |
| **금지** | `RecoveryFailedMessageClient` (HTTP) |
| **금지** | reprocess API (Phase 3) |

---

## 검증 체크리스트

### 기동

- [ ] recovery-service + processing-service + account-service + order-service 기동
- [ ] RabbitMQ Management UI에서 ingest 큐·DLQ·binding 확인

### E2E — S2 (복구 불가)

1. [ ] 존재하지 않는 `userId` 이벤트 발행
2. [ ] ingest 큐 → recovery Consumer → `failed_messages`: `failure_type = BUSINESS`
3. [ ] `message_process_logs`: `FAILED`
4. [ ] main queue **requeue 없음**

5. [ ] 미지원 `eventVersion` → ingest 적재 + ack

### E2E — S3 (일시 오류)

1. [ ] account 5xx 모킹 (또는 account 중지)
2. [ ] `message_process_logs`: `RETRY`, `retry_count` 증가
3. [ ] `max_retry_count` 초과 후 ingest 큐 publish
4. [ ] recovery Consumer → `failed_messages` upsert

### 멱등

- [ ] 동일 `(consumer_name, event_id)` 재publish → 단일 행 유지

### 회귀

- [ ] 정상 이벤트 E2E — 기존 SUCCESS 흐름 유지
- [ ] 중복 eventId — DUPLICATE, recovery 미개입
- [ ] order Outbox 재시도 — 기존과 동일

---

## 롤백

| 조치 | 방법 |
|------|------|
| ErrorHandler·Publisher revert | processing revert → 기존 rethrow 동작 |
| MQ | ingest·DLQ·main queue purge (개발) |
| recovery Consumer | `@RabbitListener` 비활성화 또는 recovery 중지 |

---

## 커밋 메시지 예시

```text
feat(processing-service): [point] 실패 MQ publish 및 ingest 큐 연동

feat(core-service): [key] failed-messages ingest 큐 및 DLQ binding 추가

feat(recovery-service): [recovery] FailedMessage ingest Consumer 구현
```

Phase 단위로 **3개 커밋 분리** 권장 (core / processing / recovery).

---

## 다음 단계

[Phase 3: 재처리](phase-03-reprocess.md)
