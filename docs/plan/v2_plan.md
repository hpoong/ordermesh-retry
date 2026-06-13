# UserPointChanged 구현 Plan v2 (Processing 오케스트레이션)

> **이 문서는 `UserPointChanged` 기능의 v2 아키텍처·구현 기준서입니다.**
> v2 관련 코드를 작성·수정하는 에이전트는 **반드시 이 plan에 정의된 서비스 경계, 데이터 소유, 처리 순서, 멱등·일관성 규칙을 따릅니다.**
> plan에 없는 방식으로 구현하지 않습니다. plan과 다른 구현이 필요하면 plan을 먼저 수정합니다.

### v1과의 관계

| 문서 | 역할 |
|------|------|
| [`plan.md`](plan.md) | v1 — account-service 단일 소비·단일 트랜잭션 (**역사 참고용**) |
| **본 문서 (v2)** | processing-service 오케스트레이션 + account internal API |

**신규 구현·운영 기준은 v2만 따른다.** v1 MQ Consumer·v1 큐·v1 routing key는 Phase 4에서 레거시 제거 대상이다.

마이그레이션 실행 순서: [v2-migration/README.md](v2-migration/README.md)

---

## 1. 아키텍처 원칙

### 1.1 서비스 경계

| 서비스 | 역할 | 소유 테이블 |
|--------|------|-------------|
| **order-service** | 포인트 결과 **결정** (`balanceAfter`), Outbox, MQ **v2 발행** | `orders`, `event_logs` |
| **processing-service** | 이벤트 **소비·오케스트레이션**, 처리 이력·관제 | `point_histories`, `message_process_logs` |
| **account-service** | 계정 도메인, `users` **잔액 반영**, 캐시 | `users`, `products` |
| **recovery-service** | 실패 메시지 적재, DLQ·재처리 | `failed_messages` |
| **core-service** | 공유 계약 (DTO, 상수, 예외, MQ/Redis 설정) — **비즈니스 로직 없음** | (없음) |

### 1.2 핵심 원칙

1. **한 이벤트 타입당 MQ 소비 주체는 processing-service 하나**
2. **order-service가 v2 MQ 계약으로 발행** — exchange·routing key·eventVersion v2
3. **`users` 변경은 account-service만** — processing은 직접 `users` 테이블에 쓰지 않는다
4. **`point_histories` 적재는 processing-service만**
5. **잔액 권위는 order-service** — account는 `balanceAfter`를 검증·재계산하지 않고 반영만
6. **일관성 모델: at-least-once + `eventId` 멱등 (processing 단일)** — 서비스 간 2단계 처리, 원자적 분산 트랜잭션 사용하지 않음
7. **REST → `CoreException`, 비REST → 도메인 예외** — [`exception-handling-convention.md`](../convention/exception-handling-convention.md) 준수

---

## 2. 전체 흐름

```
[order-service]                    [RabbitMQ v2]           [processing-service]              [account-service]

UserPointChangedOutboxService
  └─ event_logs INSERT (READY)
     eventVersion=v2, routingKey=user.point.changed.v2
         │
EventLogPublishScheduler
         │
EventLogPublishService
  └─ UserPointChangedEventPublisher
        └─ send → user.events.v2 / user.point.changed.v2
                                    │
                                    ▼
                         processing-service.user.point.changed.v2
                                    │
                         UserPointChangedConsumer
                                    │
                         UserPointChangedEventDispatcher
                                    │
                         UserPointChangedV2EventHandler
                                    │
                         UserPointChangedProcessService
                           ├─ message_process_logs (RECEIVED → PROCESSING)
                           ├─ 멱등 검사 (message_process_logs / point_histories)
                           ├─ AccountPointApplyClient ──────────────► POST /internal/v1/users/point-changed
                           │                                              ├─ users.point_balance UPDATE
                           │                                              └─ Redis 캐시 무효화
                           ├─ point_histories INSERT (SUCCESS)
                           └─ message_process_logs (SUCCESS)

실패 시:
  processing → message_process_logs FAILED → retry 정책 → recovery-service (failed_messages) / DLQ
```

### 2.1 v1과의 차이

| 항목 | v1 (`plan.md`) | v2 (본 문서) |
|------|----------------|--------------|
| MQ 발행 | `user.events.v1` / `user.point.changed` | **`user.events.v2` / `user.point.changed.v2`** |
| `eventVersion` | `v1` | **`v2`** |
| MQ 소비 | account-service | **processing-service** |
| `point_histories` | account-service | **processing-service** |
| `users` 갱신 | Consumer 내부 | **account internal API** |
| `message_process_logs` | 없음 | **processing-service** |
| 트랜잭션 | users + point_histories 단일 TX | **2단계** (account 반영 → point_histories) |
| MQ Queue | `account-service.user.point.changed.v1` | **`processing-service.user.point.changed.v2`** |

---

## 3. 기능 정의

주문 확정 등 도메인 이벤트 발생 시, order-service가 Outbox에 **v2** 이벤트를 기록하고 RabbitMQ로 발행한다.
processing-service가 이를 소비하여 **오케스트레이션**하고, account-service에 잔액 반영을 **요청**한 뒤 `point_histories`를 적재한다.

| # | 처리 | 담당 서비스 | 테이블 | 동작 |
|---|------|-------------|--------|------|
| 1 | 처리 로그 수신 | processing | `message_process_logs` | `RECEIVED` 기록 |
| 2 | 멱등 검사 | processing | `message_process_logs`, `point_histories` | 중복 시 `DUPLICATE` 후 ack |
| 3 | 잔액 반영 요청 | processing → account | `users` | internal API 호출, `balanceAfter` 설정 |
| 4 | 포인트 이력 적재 | processing | `point_histories` | `event_id` 기준 멱등 INSERT, `SUCCESS` |
| 5 | 처리 완료 로그 | processing | `message_process_logs` | `SUCCESS`, `acked_at` |
| 6 | 실패 기록 | processing → recovery | `failed_messages` | 재시도 초과·복구 불가 시 |

---

## 4. order-service 구현 기준 (발행 측)

v1 [`plan.md`](plan.md) 섹션 3과 **구조는 동일**하나, **MQ·이벤트 계약은 v2**를 사용한다.

### 4.1 Outbox 기록

**파일:** `order-service/.../outbox/UserPointChangedOutboxService.java`

| 항목 | v2 값 |
|------|-------|
| `UserPointChangedEvent.eventVersion` | `EventVersions.V2` (`"v2"`) |
| `EventLog.eventVersion` | `EventVersions.V2` |
| `EventLog.exchangeName` | `RabbitMqKeys.UserPointChanged.EXCHANGE` → `user.events.v2` |
| `EventLog.routingKey` | `RabbitMqKeys.UserPointChanged.ROUTING_KEY` → `user.point.changed.v2` |

### 4.2 발행 파이프라인 (유지)

- Outbox: `UserPointChangedOutboxService.record()`
- 스케줄러: `EventLogPublishScheduler`
- 발행: `EventLogPublishService` → `UserPointChangedEventPublisher`
- payload: `UserPointChangedEvent` JSON (필드 구조 동일, `eventVersion`만 `v2`)

### 4.3 in-flight Outbox (v1 → v2 전환 시)

`event_logs`에 `event_version = v1` 또는 `routing_key = user.point.changed`인 `READY`/`RETRYING` 건:

| 환경 | 정책 |
|------|------|
| 로컬 | 수동 purge 또는 v2로 재기록 |
| 운영 | v2 배포 전 drain 완료 후 전환 |

v1 routing key로 발행된 메시지는 v2 Consumer가 **수신하지 않는다.**

---

## 5. processing-service 구현 기준 (소비·오케스트레이션)

### 5.1 레이어 책임

| 레이어 | 클래스 | 책임 | 하지 않는 것 |
|--------|--------|------|-------------|
| Consumer | `UserPointChangedConsumer` | MQ 수신, 로그, Dispatcher 호출 | 비즈니스 로직, account API 직접 호출 |
| Dispatcher | `UserPointChangedEventDispatcher` | `eventType` 검증, `eventVersion`별 Handler 라우팅 | DB 접근, HTTP 호출 |
| Handler | `UserPointChangedV2EventHandler` | v2 ProcessService 위임 | 직접 DB·HTTP 접근 |
| ProcessService | `UserPointChangedProcessService` | 검증, 멱등, 관제 로그, account 호출, 이력 적재 | MQ 수신, 버전 분기 |
| Client | `AccountPointApplyClient` | account internal API HTTP 호출 | 비즈니스 판단 |

### 5.2 Consumer

```java
@RabbitListener(queues = RabbitMqKeys.UserPointChanged.QUEUE)
public void consumeUserPointChanged(UserPointChangedEvent message) { ... }
```

- `QUEUE` = `processing-service.user.point.changed.v2`

### 5.3 처리 순서 (`UserPointChangedProcessService.processV2`)

아래 순서를 **반드시** 따른다.

1. v2 payload 필수값 검증: `userId`, `orderId`, `pointType`, `changeAmount`, `balanceAfter`
2. `pointType` enum 검증: `EARN`, `CANCEL`, `EXPIRE`
3. `message_process_logs`에 `RECEIVED` 기록 (`trace_id`, `consumer_name`, `queue_name` 포함)
4. `event_id` 중복 검사
   - `point_histories.event_id` 존재 또는 동일 `event_id`+`consumer_name`으로 이미 `SUCCESS`/`DUPLICATE` → warn, `DUPLICATE` 기록, **정상 종료 (ack)**
5. `message_process_logs` → `PROCESSING`
6. `AccountPointApplyClient.apply(event)` — account internal API 호출
7. account 성공 응답 후 `point_histories` INSERT
   - `process_status = SUCCESS`
   - `processed_at = now`
   - `point_amount = changeAmount`
   - `balance_after = balanceAfter`
8. 동시 중복 INSERT 시 `DataIntegrityViolationException` catch → warn, `DUPLICATE` 처리 후 **정상 종료 (ack)**
9. `message_process_logs` → `SUCCESS`, `processed_at`, `acked_at` 기록

### 5.4 멱등성 규칙 (processing)

- 멱등 키: `event_id`
- 1차: `pointHistoryRepository.existsByEventId(eventId)`
- 2차: `message_process_logs`에서 동일 consumer·event_id 완료 여부
- 3차: DB UNIQUE (`uk_point_histories_event_id`) + `DataIntegrityViolationException`
- 중복 메시지는 **예외 throw 없이** ack

### 5.5 예외 정책 (processing — 비REST)

| 상황 | 처리 |
|------|------|
| 중복 `eventId` | warn, `DUPLICATE` 로그, skip (ack) |
| payload 필수값 누락 | `UserPointChangedProcessException` throw |
| 잘못된 `pointType` | `IllegalArgumentException` 또는 `UserPointChangedProcessException` |
| 미지원 `eventVersion` | `UnsupportedUserPointChangedVersionException` throw |
| 잘못된 `eventType` | `IllegalArgumentException` throw |
| account API 4xx (사용자 없음 등) | `message_process_logs` FAILED, 복구 불가 시 recovery 위임 |
| account API 5xx / 타임아웃 | `message_process_logs` FAILED → **retry** |
| `point_histories` INSERT 실패 (account 성공 후) | `message_process_logs` FAILED → **retry** (`balanceAfter` 재설정은 안전) |

`CoreException`을 processing에서 사용하지 않는다.

### 5.6 엔티티·Enum (processing-service)

| 클래스 | 위치 | 용도 |
|--------|------|------|
| `PointHistory` | `processing-service/.../entity/` | `point_histories` 매핑 |
| `MessageProcessLog` | `processing-service/.../entity/` | `message_process_logs` 매핑 |
| `PointHistoryRepository` | `processing-service/.../repository/` | `existsByEventId()` |
| `MessageProcessLogRepository` | `processing-service/.../repository/` | 처리 상태 추적 |
| `PointType` | `processing-service/.../enums/` 또는 core 공유 검토 | `EARN`, `CANCEL`, `EXPIRE` |
| `PointProcessStatus` | `processing-service/.../enums/` | `READY`, `SUCCESS`, `FAILED` |
| `MessageProcessStatus` | `processing-service/.../enums/` | `RECEIVED`, `PROCESSING`, `SUCCESS`, `FAILED`, `DUPLICATE`, `RETRY`, `DLQ` |

`point_histories`, `message_process_logs` 접근은 **processing-service에서만** 수행한다.

---

## 6. account-service 구현 기준 (잔액 반영)

### 6.1 역할

- **MQ Consumer 없음** — v2에서 account는 MQ를 소비하지 않는다
- **Internal API 제공** — processing만 호출 가능한 잔액 반영 엔드포인트
- v1 `consumer/user/point/*` — Phase 4에서 **삭제**

### 6.2 Internal API

```
POST /internal/v1/users/point-changed
```

Request body (`UserPointChangedApplyRequest`):

```java
public record UserPointChangedApplyRequest(
    String eventId,
    Long userId,
    Long orderId,
    String pointType,
    Integer changeAmount,
    Integer balanceAfter,
    LocalDateTime occurredAt
) {}
```

Response: `200 OK`

> 멱등은 **processing** `point_histories`·`message_process_logs`에서 처리한다. account internal API는 **잔액 반영만** 수행한다.

### 6.3 처리 순서 (`UserPointApplyService.apply`)

1. Request 필수값 검증 → 실패 시 `CoreException.badRequest`
2. `users` 비관적 락 조회 (`findByIdForUpdate`) → 없으면 `404` (`CoreException.notFound`)
3. `userEntity.updatePointBalance(balanceAfter)` — **누적 계산 아님, 직접 설정**
4. `UserDetailCacheEvictEvent` 발행 → Redis 캐시 무효화
5. `200` 반환

processing 재시도로 동일 API가 재호출되더라도 `balanceAfter` **직접 설정**이므로 자연 멱등이다.

### 6.4 account 엔티티·Repository

| 클래스 | 용도 |
|--------|------|
| `UserEntity` | `users`, `updatePointBalance()` |
| `UserRepository` | `findByIdForUpdate()` |

> v2에서 account는 `point_histories`를 **읽거나 쓰지 않는다.** 이력·멱등은 processing이 소유한다.

### 6.5 예외 정책 (account internal API)

Internal API는 HTTP 경계이므로 **`CoreException`** 사용.

| 상황 | HTTP | 예외 |
|------|------|------|
| 필수값 누락 | 400 | `CoreException.badRequest` |
| 사용자 미존재 | 404 | `CoreException.notFound` |

---

## 7. core-service 공유 계약

### 7.1 이벤트 DTO

`UserPointChangedEvent` record **필드 구조 유지**, v2에서는 `eventVersion = "v2"`.

```java
public record UserPointChangedEvent(
    String eventId,
    String eventType,      // USER_POINT_CHANGED
    String eventVersion,   // v2
    Long userId,
    Long orderId,
    String pointType,      // EARN | CANCEL | EXPIRE
    Integer changeAmount,
    Integer balanceAfter,
    LocalDateTime occurredAt
) {}
```

### 7.2 MQ 키 (v2 — 유일 기준)

| 상수 | v1 (레거시·제거) | **v2 값** |
|------|------------------|-----------|
| `EXCHANGE` | `user.events.v1` | **`user.events.v2`** |
| `ROUTING_KEY` | `user.point.changed` | **`user.point.changed.v2`** |
| `QUEUE` | `account-service.user.point.changed.v1` | **`processing-service.user.point.changed.v2`** |
| `DLQ` | `{v1_queue}.dlq` | **`processing-service.user.point.changed.v2.dlq`** |

- Queue 이름은 **consumer 소유권** 규칙에 따라 processing-service 기준.
- [`rabbitmq-key-convention.md`](../convention/rabbitmq-key-convention.md) 준수.
- `PROCESSING_QUEUE` 등 fan-out용 이중 상수 패턴 **사용하지 않는다.**

**구현 시 주의:** 이전 fan-out Phase 1 코드(`PROCESSING_QUEUE` + 동일 routing key)가 있다면 revert 후 v2 계약으로 재작성한다.

### 7.3 기타 상수

| 상수 클래스 | 값 |
|------------|-----|
| `EventLogTypes.USER_POINT_CHANGED` | `"USER_POINT_CHANGED"` |
| `EventVersions.V1` | `"v1"` (레거시 참고) |
| `EventVersions.V2` | **`"v2"`** (신규·유일 발행 버전) |

---

## 8. recovery-service 연동

### 8.1 역할

- processing에서 **복구 불가** 또는 **최대 재시도 초과** 시 `failed_messages` 적재
- DLQ 메시지와 연계한 재처리·운영 조회 (후속 구현)

### 8.2 연동 시점

| 조건 | 동작 |
|------|------|
| account 404 (사용자 없음) | `failed_messages` 적재, DLQ 또는 skip 정책 (운영 알림) |
| 미지원 eventVersion | `failed_messages` 적재 |
| retry 횟수 초과 | `failed_messages` + DLQ |

---

## 9. 일관성·부분 실패 처리

### 9.1 2단계 처리 시나리오

| 순서 | account | point_histories | 재시도 시 |
|------|---------|-----------------|-----------|
| 정상 | 성공 | 성공 | — |
| A | 성공 | 실패 | account `balanceAfter` 재설정 (동일 값), point_histories 재시도 |
| B | 실패 | (미실행) | 전체 재시도 |
| C | 타임아웃 | 불명 | account 재호출 후 point_histories 재시도 |

### 9.2 보상 트랜잭션

- v2에서는 **Saga 보상(잔액 롤백)을 하지 않는다.**
- order-service가 `balanceAfter` 권위를 갖고, account는 `balanceAfter` **직접 설정**으로 재호출에 안전하다.
- 운영 불일치는 `event_id` 기준 대조(processing `point_histories` vs `users.point_balance`)로 감지한다.

---

## 10. DB 반영 규칙

### 10.1 `users` (account-service)

- 갱신: `balanceAfter` **직접 설정**
- 잔액 권위: **order-service**

### 10.2 `point_histories` (processing-service)

| 컬럼 | 값 |
|------|-----|
| `user_id` | `event.userId` |
| `order_id` | `event.orderId` |
| `point_type` | `event.pointType` |
| `point_amount` | `event.changeAmount` |
| `balance_after` | `event.balanceAfter` |
| `process_status` | `SUCCESS` |
| `event_id` | `event.eventId` (UNIQUE) |
| `processed_at` | 처리 시각 |

### 10.3 `message_process_logs` (processing-service)

| 컬럼 | 주요 값 |
|------|---------|
| `process_status` | `RECEIVED` → `PROCESSING` → `SUCCESS` / `FAILED` / `DUPLICATE` |
| `event_id` | 멱등·추적 키 |
| `consumer_name` | 예: `UserPointChangedConsumer` |
| `trace_id` | 요청 상관관계 ID |
| `duplicate_yn` | `Y` / `N` |

---

## 11. 필수 파일 구조 (v2)

### order-service (v2 발행)

```
api/user/
  UserPointChangedOutboxController.java
  UserPointChangedOutboxRequest.java
outbox/
  UserPointChangedOutboxService.java    # EventVersions.V2, v2 MQ key
publisher/
  EventLogPublishService.java
  EventLogPublishTransactionService.java
  UserPointChangedEventPublisher.java
scheduler/
  EventLogPublishScheduler.java
```

### processing-service (신규)

```
consumer/
  UserPointChangedConsumer.java         # @RabbitListener(queues = QUEUE)
  UserPointChangedEventDispatcher.java
  handler/
    UserPointChangedEventHandler.java
    UserPointChangedV2EventHandler.java
  service/
    UserPointChangedProcessService.java # processV2
  client/
    AccountPointApplyClient.java
  exception/
    UserPointChangedProcessException.java
    UnsupportedUserPointChangedVersionException.java
entity/
  PointHistory.java
  MessageProcessLog.java
enums/
  PointType.java
  PointProcessStatus.java
  MessageProcessStatus.java
repository/
  PointHistoryRepository.java
  MessageProcessLogRepository.java
```

### account-service

```
api/
  user/
    controller/UserController.java          # 기존 REST 유지
    service/UserService.java
  internal/
    user/
      UserPointChangedInternalController.java   # 신규
      UserPointChangedApplyRequest.java         # 신규
      UserPointApplyService.java                # 신규
entity/
  UserEntity.java
repository/
  UserRepository.java

# Phase 4 제거 대상 (v1 레거시)
consumer/user/point/
entity/PointHistoryEntity.java
repository/PointHistoryRepository.java
```

### core-service

```
event/UserPointChangedEvent.java
keys/rabbitmq/RabbitMqKeys.java                 # v2 단일 QUEUE/EXCHANGE/ROUTING_KEY
keys/event/EventLogTypes.java
keys/event/EventVersions.java                   # V2 추가
config/RabbitMqConfig.java                      # v2 queue·binding만
```

### recovery-service (후속)

```
# failed_messages 적재·재처리 (v2 후속 스프린트)
```

### 테스트 UI

```
front/index.html    # Outbox record (payload eventVersion은 서버가 v2로 기록)
```

---

## 12. v2 구현 체크리스트

1. [ ] `EventVersions.V2` 추가
2. [ ] `RabbitMqKeys.UserPointChanged` v2 계약 (exchange·routing key·queue)
3. [ ] `RabbitMqConfig` v2 exchange·queue·binding만 선언 (v1 Bean 제거)
4. [ ] order `UserPointChangedOutboxService` v2 발행 반영
5. [ ] in-flight v1 `event_logs` 처리
6. [ ] processing-service: Consumer 파이프라인 + `message_process_logs` 구현
7. [ ] processing-service: `AccountPointApplyClient`
8. [ ] account-service: `/internal/v1/users/point-changed` internal API
9. [ ] Phase 4: account `consumer/user/point/*`·`PointHistory*` 제거
10. [ ] Phase 4: v1 MQ 리소스·문서 정리
11. [ ] recovery-service: `failed_messages` 연동 (후속)
12. [ ] `docs/process/*.md`, `docs/db/tables.md` v2 기준 갱신
13. [ ] E2E 검증 (섹션 13)

---

## 13. 검증 기준

### E2E 흐름 (v2 단일 경로)

1. `front/index.html`에서 Outbox record 실행
2. `event_logs`에 `publish_status = READY`, `event_version = v2`, `routing_key = user.point.changed.v2` 확인
3. 스케줄러 실행 후 `publish_status = PUBLISHED` 확인
4. processing-service 로그: `UserPointChanged 이벤트 처리 완료`
5. `message_process_logs`에 `SUCCESS` 행 확인
6. `users.point_balance = balanceAfter` 확인
7. `point_histories`에 `event_id`, `process_status = SUCCESS` 행 확인
8. Redis 사용자 캐시 무효화 확인

### 필수 동작

- [ ] 동일 `eventId` 재수신 시 processing에서 `DUPLICATE` skip, account API 미중복 호출
- [ ] 존재하지 않는 `userId` 시 account 404 → processing FAILED
- [ ] account 성공 후 `point_histories` 실패 시 재시도로 이력 복구
- [ ] 미지원 `eventVersion` 시 `UnsupportedUserPointChangedVersionException`
- [ ] 포인트 변경 후 Redis 사용자 캐시 무효화
- [ ] v1 routing key로 발행된 메시지는 processing Consumer가 수신하지 않음

### 사전 조건

- DB에 `users(id)`, `orders(id)` FK 대상 데이터 존재
- account-service internal API 네트워크 접근 가능 (processing → account)
- RabbitMQ에 `user.events.v2` exchange·`processing-service.user.point.changed.v2` 큐·binding 존재

---

## 14. 설계 결정 사항

| 항목 | v2 결정 |
|------|---------|
| 아키텍처 | processing 오케스트레이션 + account internal API |
| MQ 발행 | **order-service v2 계약** (exchange·routing key·eventVersion) |
| MQ 소비 | processing-service 단독 |
| v1 fan-out / cutover | **사용하지 않음** — routing key 분리로 v1·v2 MQ 경로 분리 |
| 잔액 갱신 | account internal API |
| `point_histories` 소유 | processing-service |
| 멱등 | **processing `event_id` 단일** (`point_histories`·`message_process_logs`) |
| 일관성 | at-least-once + eventId 멱등 (분산 TX 없음) |
| 잔액 권위 | order-service |
| 잔액 갱신 방식 | `balanceAfter` 직접 설정 |
| 보상 트랜잭션 | 없음 |
| 중복 처리 | warn + skip/멱등 (예외 throw 금지) |

---

## 15. 코딩 규칙

1. **이벤트 계약 변경 시** `UserPointChangedEvent` + Outbox + `front/index.html` **함께** 수정
2. **서비스 경계** — processing이 `users` 직접 수정 금지, account가 `point_histories` 접근 금지
3. **멱등성** — processing `event_id` 단일 (`point_histories`·`message_process_logs`)
4. **예외** — [`exception-handling-convention.md`](../convention/exception-handling-convention.md) 준수
5. **MQ/이벤트 상수** — `core-service` 상수만 사용
6. **plan 우선** — 본 문서에 없는 필드·흐름 임의 추가 금지

---

## 변경 이력

| 일자 | 내용 |
|------|------|
| 2026-06-06 | v2 최초 작성 — processing 오케스트레이션 + account internal API |
| 2026-06-13 | v2 전면 개정 — order v2 발행, routing key 분리, fan-out/cutover 전략 폐기 |
| 2026-06-13 | `user_point_applied_events` 제거 — 멱등 processing 단일 책임 |
