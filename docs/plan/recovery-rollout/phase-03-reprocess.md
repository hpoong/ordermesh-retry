# Phase 3: 재처리(reprocess)

> **선행:** [Phase 2](phase-02-dlq-and-processing-failure-handling.md)  
> **후행:** [Phase 4](phase-04-docs-and-e2e.md)  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 8, [failed_messages 스키마](../../../core-service/src/main/resources/db/migration/V1__create_tables.sql)

---

## 목적

ingest 큐를 통해 `failed_messages`에 적재된 실패 건을 **원본 exchange/routing_key로 재발행**하여 processing이 다시 처리할 수 있게 한다.

수동 reprocess API를 먼저 구현하고, (선택) 자동 Scheduler를 추가한다.

---

## 전제조건

- [ ] Phase 2: ingest 큐 → `failed_messages` E2E 완료
- [ ] processing v2 Consumer·멱등 검사(`eventId`) 정상 동작 확인
- [ ] 재처리 대상 `exchange_name`, `routing_key`가 `failed_messages`에 저장됨

---

## 시나리오 정책 (S5. 재처리)

| 단계 | reprocess_status | 동작 |
|------|------------------|------|
| 대기 | `WAITING` | 운영 조회·재처리 대상 |
| 재처리 시작 | `PROCESSING` | claim (동시 재처리 방지) |
| 재발행 성공 | `SUCCESS` | `reprocessed_at` 기록 |
| 재발행 실패 | `FAILED` | `failure_reason` 갱신, `WAITING` 복귀 또는 수동 개입 |

**재발행 대상:**

- `exchange_name` + `routing_key` (ingest 시 저장된 메타)
- 대상 큐: `processing-service.user.point.changed.v2`

**재처리 시 멱등:**

processing의 `eventId` 중복 검사가 있으므로, 이미 `SUCCESS`/`DUPLICATE`된 이벤트는 안전하게 skip됨.

**자동 reprocess 범위 (선택, 권장):**

| failure_type | 자동 재처리 |
|--------------|-------------|
| `BUSINESS` | **수동만** — 데이터 오류 재발행 방지 |
| `SYSTEM` | Scheduler 대상 (선택) |
| `TIMEOUT` | Scheduler 대상 (선택) |

---

## 작업 목록

### 1. `FailedMessageReprocessService`

**패키지:** `recovery-service/src/main/java/com/hopoong/recovery/reprocess/`

처리 순서:

1. `id`로 `FailedMessage` 조회
2. `reprocess_status = WAITING` 검증
3. `PROCESSING`으로 claim (낙관적/비관적 lock)
4. `payload`를 `UserPointChangedEvent`로 역직렬화
5. `RabbitTemplate.convertAndSend(exchangeName, routingKey, event)` 재발행
6. 성공 → `SUCCESS`, `reprocessed_at = now`
7. 실패 → `FAILED`, `failure_reason` 갱신

### 2. Internal API — reprocess·조회

**파일:** `FailedMessageInternalController.java` (Phase 1 확장)

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/internal/v1/failed-messages/{id}/reprocess` | 단건 재처리 |
| `POST` | `/internal/v1/failed-messages/reprocess` | (선택) 복수 ID 일괄 재처리 |
| `GET` | `/internal/v1/failed-messages` | Phase 1 조회 API 유지 |

> 실패 **적재**는 MQ(Phase 2)만 사용. reprocess API는 **운영·E2E**용 HTTP.

### 3. (선택) `FailedMessageReprocessScheduler`

**파일:** `recovery-service/.../reprocess/FailedMessageReprocessScheduler.java`

조건 예시:

- `reprocess_status = WAITING`
- `failure_type IN (SYSTEM, TIMEOUT)`
- `last_failed_at` + N분 경과

### 4. `FailedMessage` 도메인 메서드

| 메서드 | 설명 |
|--------|------|
| `claimForReprocess()` | `WAITING` → `PROCESSING` |
| `markReprocessSuccess()` | → `SUCCESS`, `reprocessedAt` |
| `markReprocessFailed(reason)` | → `FAILED` 또는 `WAITING` 복귀 |

### 5. RabbitTemplate 설정

- recovery-service에 `RabbitTemplate` Bean (core `RabbitMqConfig` import)
- MessageConverter는 core `Jackson2JsonMessageConverter`와 동일 계약 유지

---

## 패키지 구조 (Phase 3 추가)

```
recovery-service/src/main/java/com/hopoong/recovery/
└── reprocess/
    ├── FailedMessageReprocessService.java
    ├── FailedMessageReprocessScheduler.java   # 선택
    └── FailedMessageReprocessProperties.java
```

---

## 파일 변경 요약

| 구분 | 범위 |
|------|------|
| **신규** | `recovery-service/.../reprocess/**` |
| **수정** | `FailedMessage.java`, `FailedMessageInternalController.java` |
| **수정** | `recovery-service/application.yml` |
| **금지** | processing-service 변경 (멱등은 기존 로직 활용) |

---

## 검증 체크리스트

### 수동 reprocess (Phase 3-A)

1. [ ] Phase 2에서 ingest 경유 `BUSINESS` 실패 건 1건 확보
2. [ ] 실패 원인 해소 (예: userId 생성, account 정상화)
3. [ ] `POST /internal/v1/failed-messages/{id}/reprocess` 호출
4. [ ] main queue → processing → `message_process_logs` `SUCCESS`
5. [ ] `failed_messages.reprocess_status = SUCCESS`, `reprocessed_at` 기록

### 재처리 실패

- [ ] account 여전히 5xx → reprocess `FAILED` 또는 `WAITING` 복귀

### 멱등·중복

- [ ] 이미 processing `SUCCESS`인 eventId reprocess → processing `DUPLICATE`

### (선택) 자동 Scheduler (Phase 3-B)

- [ ] `SYSTEM` 실패 건, `min-wait-minutes` 경과 후 자동 reprocess
- [ ] `BUSINESS` 건은 Scheduler 대상에서 제외

---

## 롤백

| 조치 | 방법 |
|------|------|
| reprocess API 비활성 | Controller endpoint 제거 또는 feature flag |
| Scheduler | `app.recovery.reprocess.enabled=false` |
| 잘못 재발행된 메시지 | main queue purge (개발) |

---

## 커밋 메시지 예시

```text
feat(recovery-service): [recovery] failed-messages 재처리 API 구현
```

---

## 다음 단계

[Phase 4: 문서·E2E](phase-04-docs-and-e2e.md)
