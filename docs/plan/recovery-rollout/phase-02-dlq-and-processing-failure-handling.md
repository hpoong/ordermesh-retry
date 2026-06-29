# Phase 2: MQ DLQ + processing 실패 분기 + DLQ Consumer

> **선행:** [Phase 1](phase-01-recovery-bootstrap-and-ingest-api.md)  
> **후행:** [Phase 3](phase-03-reprocess.md)  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 5.5, 8.2, [exception-handling-convention](../../convention/exception-handling-convention.md)

---

## 목적

processing 소비 실패를 **유형별로 분기**하고, 복구 불가 건은 recovery API로 적재하며, 일시 오류는 retry 후 DLQ로 이동시킨다.

recovery-service DLQ Consumer가 DLQ 메시지를 `failed_messages`에 동기화한다.

---

## 전제조건

- [ ] Phase 1: recovery Internal API 기동·수동 적재 검증 완료
- [ ] Phase 2 착수 전 **retry 메커니즘** 확정 (Spring AMQP RetryInterceptor 권장)
- [ ] RabbitMQ docker-compose 기동 가능

---

## 시나리오 정책 (이 Phase 범위)

### S2. 복구 불가 — 즉시 failed_messages 적재

| 조건 | failure_type | message_process_logs | MQ | recovery |
|------|--------------|----------------------|-----|----------|
| 필수값 누락 / 잘못된 pointType | `BUSINESS` | `FAILED` | ack (requeue 금지) | Internal API |
| 미지원 eventVersion | `BUSINESS` | `FAILED` | ack | Internal API |
| 잘못된 eventType | `BUSINESS` | `FAILED` | ack | Internal API |
| account 404 | `BUSINESS` | `FAILED` | ack | Internal API |

**핵심:** 예외 rethrow 대신 **실패 기록 → recovery API 호출 → 정상 ack**.

### S3. 일시 오류 — retry 후 DLQ

| 조건 | failure_type | 1~N회 | retry 초과 |
|------|--------------|-------|------------|
| account 5xx | `SYSTEM` | `RETRY`, requeue | DLQ 이동 |
| account 타임아웃/네트워크 | `TIMEOUT` | 동일 | DLQ 이동 |
| point_histories INSERT 실패 (account 성공 후) | `SYSTEM` | 동일 (멱등 안전) | DLQ 이동 |

**retry_count 정책:**

- `message_process_logs.retry_count` — processing 처리 단위 추적
- `failed_messages.retry_count` — DLQ 적재 시점 누적
- `max_retry_count` 기본값 3 (`application.yml` 오버라이드 가능)

### S4. DLQ 소비 → failed_messages 동기화

recovery DLQ Consumer 수신 시:

1. payload 파싱 (`UserPointChangedEvent`)
2. `(consumer_name, event_id)` UK 기준 upsert
3. `dlq_stored_yn = Y`, `reprocess_status = WAITING`
4. DLQ 메시지 ack

이미 API로 적재된 건이면 `dlq_stored_yn`만 갱신 (멱등).

---

## 작업 목록

### 1. core-service — DLQ RabbitMQ Bean

**파일:** `core-service/src/main/java/com/hopoong/core/config/RabbitMqConfig.java`

| Bean | 설명 |
|------|------|
| `userPointChangedDlq()` | `processing-service.user.point.changed.v2.dlq` Queue (durable) |
| `userPointChangedQueue()` 수정 | `x-dead-letter-exchange`, `x-dead-letter-routing-key` 추가 |
| DLQ binding | DLQ 큐를 exchange에 binding |

**상수:** `RabbitMqKeys.UserPointChangedV2.DLQ` (기존)

### 2. processing-service — 예외 분류 보강

**파일:** `AccountPointApplyClient.java`

- 4xx 응답 시 HTTP status code를 예외에 포함 (404 vs 기타 4xx 구분 가능)
- 5xx / `RestClientException` → retry 대상으로 분류 가능하게 유지

**파일:** `UserPointChangedProcessService.java`

| 변경 | 내용 |
|------|------|
| `RETRY` 상태 전이 | 일시 오류 시 `markRetry()` |
| `DLQ` 상태 전이 | retry 소진 또는 복구 불가 DLQ 위임 시 |
| `retry_count` 증가 | 재시도마다 increment |
| 복구 불가 catch | recovery API 호출 후 예외 **미전파** (ack) |

**파일:** `MessageProcessLog.java`

- `markRetry()`, `markDlq()` 메서드 추가

### 3. processing-service — `RecoveryFailedMessageClient`

**파일:** `processing-service/.../client/RecoveryFailedMessageClient.java`

- `POST {app.recovery.base-url}/internal/v1/failed-messages`
- 실패 컨텍스트(eventId, queue, exchange, routingKey, payload, failureType, failureReason) 전달
- recovery API 실패 시 로그 + 예외 정책 확정 (권장: 로그 후 ack — 무한 requeue 방지)

**설정:** `processing-service/application.yml`

```yaml
app:
  recovery:
    base-url: http://localhost:9300
```

### 4. processing-service — `RabbitListenerErrorHandler`

**파일:** `processing-service/.../config/RabbitListenerErrorHandlerConfig.java` (신규)

| 예외 유형 | 정책 |
|-----------|------|
| 멱등·중복 (return 정상 종료) | ack |
| `BUSINESS` (복구 불가) | recovery API + ack |
| `SYSTEM` / `TIMEOUT` | retry → 초과 시 DLQ (nack, requeue=false) |
| `UnsupportedUserPointChangedVersionException` | `BUSINESS` → recovery API + ack |
| `IllegalArgumentException` | `BUSINESS` → recovery API + ack |

Spring AMQP `RetryInterceptor` 또는 `SimpleRabbitListenerContainerFactory` retry 설정과 연계.

### 5. recovery-service — DLQ Consumer

**파일:** `recovery-service/.../consumer/DlqMessageConsumer.java`

```java
@RabbitListener(queues = RabbitMqKeys.UserPointChangedV2.DLQ)
public void consumeDlqMessage(...) { ... }
```

- Phase 1 `FailedMessageIngestService` 재사용
- MQ 메타데이터(exchange, routingKey, queue)를 엔티티에 저장

### 6. recovery-service — AMQP 설정

- `build.gradle`: `spring-boot-starter-amqp` (Phase 1에서 미추가 시)
- `application.yml`: RabbitMQ 연결 설정

---

## 연동 방식 (하이브리드)

| 실패 유형 | 1차 처리 | failed_messages 경로 |
|-----------|----------|----------------------|
| 복구 불가 (4xx, payload, 미지원 버전) | processing | **Internal API** 후 ack |
| 일시 오류 (5xx, 네트워크) | processing + MQ retry | retry 소진 → **DLQ Consumer** |
| DLQ에만 존재 | recovery | DLQ Consumer → upsert |

---

## 파일 변경 요약

| 구분 | 범위 |
|------|------|
| **신규** | `processing-service/.../RecoveryFailedMessageClient`, ErrorHandler config |
| **신규** | `recovery-service/.../consumer/DlqMessageConsumer` |
| **수정** | `core-service/RabbitMqConfig.java` |
| **수정** | `processing-service/UserPointChangedProcessService`, `MessageProcessLog`, `AccountPointApplyClient` |
| **수정** | `processing-service/application.yml`, `recovery-service/application.yml` |
| **금지** | reprocess API (Phase 3) |

---

## 검증 체크리스트

### 기동

- [ ] recovery-service + processing-service + account-service + order-service 기동
- [ ] RabbitMQ Management UI에서 DLQ 큐·binding 확인

### E2E — S2 (복구 불가)

1. [ ] 존재하지 않는 `userId` 이벤트 발행
2. [ ] `failed_messages`: `failure_type = BUSINESS`, `reprocess_status = WAITING`
3. [ ] `message_process_logs`: `FAILED`
4. [ ] main queue **requeue 없음** (동일 메시지 무한 반복 없음)

5. [ ] 미지원 `eventVersion` 이벤트 → `failed_messages` 적재 + ack

### E2E — S3 (일시 오류)

1. [ ] account 5xx 모킹 (또는 account 중지)
2. [ ] `message_process_logs`: `RETRY`, `retry_count` 증가
3. [ ] `max_retry_count` 초과 후 DLQ에 메시지 적재
4. [ ] recovery DLQ Consumer → `failed_messages` upsert, `dlq_stored_yn = Y`

### 멱등

- [ ] `(consumer_name, event_id)` UK — API + DLQ 이중 적재 시 단일 행 유지

### 회귀

- [ ] 정상 이벤트 E2E — 기존 SUCCESS 흐름 유지
- [ ] 중복 eventId — DUPLICATE, recovery 미개입
- [ ] order Outbox 재시도 — 기존과 동일

---

## 롤백

| 조치 | 방법 |
|------|------|
| ErrorHandler revert | processing revert → 기존 rethrow 동작 |
| DLQ 큐 | 개발 환경 DLQ·main queue purge |
| recovery DLQ Consumer | `@RabbitListener` 비활성화 또는 recovery 중지 |

---

## 커밋 메시지 예시

```text
feat(processing-service): [point] 실패 분기 및 recovery failed-messages 연동

feat(core-service): [key] UserPointChanged v2 DLQ 큐 및 dead-letter binding 추가

feat(recovery-service): [recovery] DLQ Consumer 및 failed-messages 동기화
```

Phase 단위로 **3개 커밋 분리** 권장 (core / processing / recovery).

---

## 다음 단계

[Phase 3: 재처리](phase-03-reprocess.md)
