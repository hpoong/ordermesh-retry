# UserPointChanged 구현 Plan

> **⚠️ v1 레거시 문서입니다.** 신규 구현·운영 기준은 [`v2_plan.md`](v2_plan.md)를 따릅니다.  
> v1 account Consumer·v1 MQ는 Phase 4에서 제거되었습니다.

> **이 문서는 `UserPointChanged` 기능의 코드 구현 기준서입니다.**
> UserPointChanged 관련 코드를 작성·수정하는 에이전트는 **반드시 이 plan에 정의된 아키텍처, 데이터 계약, 레이어 책임, 비즈니스 규칙을 따릅니다.**
> plan에 없는 방식으로 구현하지 않습니다. plan과 다른 구현이 필요하면 plan을 먼저 수정합니다.

---

## 1. 기능 정의

주문 확정 등 도메인 이벤트 발생 시, order-service가 Outbox에 이벤트를 기록하고 RabbitMQ로 발행한다.
account-service가 이를 소비하여 아래 두 가지를 **단일 트랜잭션**으로 처리한다.

| # | 처리 | 테이블 | 동작 |
|---|------|--------|------|
| 1 | 포인트 잔액 갱신 | `users` | `point_balance` ← 이벤트 `balanceAfter` |
| 2 | 포인트 이력 적재 | `point_histories` | `event_id` 기준 멱등 INSERT, `process_status = SUCCESS` |

---

## 2. 아키텍처

```
[order-service]                              [RabbitMQ]                 [account-service]

UserPointChangedOutboxService
  └─ event_logs INSERT (READY)
         │
EventLogPublishScheduler (@Scheduled)
         │
EventLogPublishService
  ├─ claimForPublish → RETRYING
  ├─ UserPointChangedEventPublisher
  │     └─ send → user.events.v1 / user.point.changed
  └─ markPublished → PUBLISHED
                                    │
                                    ▼
                         account-service.user.point.changed.v1
                                    │
                         UserPointChangedConsumer        ← MQ 수신만
                                    │
                         UserPointChangedEventDispatcher ← eventType·eventVersion 라우팅
                                    │
                         UserPointChangedV1EventHandler  ← v1 위임
                                    │
                         UserPointChangedProcessService  ← 비즈니스 로직
                           ├─ users.point_balance UPDATE
                           └─ point_histories INSERT
```

### 레이어 책임 (반드시 분리)

| 레이어 | 클래스 | 책임 | 하지 않는 것 |
|--------|--------|------|-------------|
| Consumer | `UserPointChangedConsumer` | MQ 메시지 수신, 로그, Dispatcher 호출 | 비즈니스 로직, DB 접근 |
| Dispatcher | `UserPointChangedEventDispatcher` | `eventType` 검증, `eventVersion`별 Handler 라우팅 | DB 접근, 포인트 계산 |
| Handler | `UserPointChangedV1EventHandler` | 버전별 ProcessService 위임 | 직접 DB 접근 |
| ProcessService | `UserPointChangedProcessService` | 검증, 멱등성, 잔액 갱신, 이력 적재, 캐시 무효화 | MQ 수신, 버전 분기 |

---

## 3. order-service 구현 기준 (발행 측)

### 3.1 Outbox 기록

- 진입: `UserPointChangedOutboxService.record()`
- `event_logs`에 `publish_status = READY`로 INSERT
- payload는 `UserPointChangedEvent` record를 JSON 직렬화
- `eventId`는 UUID, `eventType = USER_POINT_CHANGED`, `eventVersion = v1`
- MQ 키는 `RabbitMqKeys.UserPointChanged` 상수 사용 (하드코딩 금지)

### 3.2 스케줄러 발행

- `EventLogPublishScheduler`가 `@Scheduled(fixedDelayString)`으로 `EventLogPublishService.publishReadyEvents()` 호출
- 설정 키: `app.event-outbox.publish.*` (`application.yml`)

### 3.3 발행 오케스트레이션

`EventLogPublishService.publishReadyEvents()` 흐름:

1. `READY` / `RETRYING` 후보 조회 (`next_retry_at` 조건 포함)
2. `batch-size` 상한까지 반복
3. `claimForPublish()` — 선점 실패 시 skip
4. `UserPointChangedEventPublisher.publish()` — RabbitMQ send
5. 성공 시 `markPublished()` → `PUBLISHED`
6. 실패 시 `handlePublishFailure()` → 재시도(`RETRYING` + `next_retry_at`) 또는 `FAILED`

발행 상태값:

| 상태 | 의미 |
|------|------|
| `READY` | Outbox 적재 직후 |
| `RETRYING` | 발행 시도 중 또는 재시도 예약 |
| `PUBLISHED` | MQ 발행 + DB 반영 완료 |
| `FAILED` | 최대 재시도 초과 |

### 3.4 MQ 발행

- `UserPointChangedEventPublisher`가 `RabbitTemplate.send()` 호출
- `contentType = application/json`, `messageId = eventId`
- body는 `event_logs.payload` 원본 bytes 그대로 전송

---

## 4. account-service 구현 기준 (소비 측)

### 4.1 처리 순서 (`UserPointChangedProcessService.processV1`)

아래 순서를 **반드시** 따른다.

1. v1 payload 필수값 검증: `userId`, `orderId`, `pointType`, `changeAmount`, `balanceAfter`
2. `pointType` enum 검증: `EARN`, `CANCEL`, `EXPIRE` (`PointType.from()`)
3. `point_histories.event_id` 존재 확인 → 있으면 중복, warn 로그 후 **정상 종료 (ack)**
4. `users` 조회 → 없으면 `UserPointChangedProcessException` throw
5. `userEntity.updatePointBalance(event.balanceAfter())` — **누적 계산 아님, 직접 설정**
6. `point_histories` INSERT
   - `process_status = SUCCESS`
   - `processed_at = now`
   - `point_amount = changeAmount`
   - `balance_after = balanceAfter`
7. 동시 중복 INSERT 시 `DataIntegrityViolationException` catch → warn 로그 후 **정상 종료 (ack)**
8. `UserDetailCacheEvictEvent` 발행 → Redis 사용자 캐시 무효화

### 4.2 멱등성 규칙

- 멱등 키: `event_id` (= `point_histories.event_id` UNIQUE)
- 1차 방어: `pointHistoryRepository.existsByEventId(eventId)` 사전 조회
- 2차 방어: DB UNIQUE 제약 + `DataIntegrityViolationException` 처리
- 중복 메시지는 **예외를 던지지 않고** 정상 ack

### 4.3 예외 정책

| 상황 | 처리 |
|------|------|
| 중복 `eventId` | warn 로그, skip (ack) |
| 사용자 미존재 | `UserPointChangedProcessException` throw |
| payload 필수값 누락 | `UserPointChangedProcessException` throw |
| 잘못된 `pointType` | `IllegalArgumentException` throw |
| 미지원 `eventVersion` | `UnsupportedUserPointChangedVersionException` throw |
| 잘못된 `eventType` | `IllegalArgumentException` throw |

### 4.4 엔티티·Enum

| 클래스 | 위치 | 용도 |
|--------|------|------|
| `PointHistoryEntity` | `account-service/.../entity/` | `point_histories` 매핑 |
| `PointHistoryRepository` | `account-service/.../repository/` | `existsByEventId()` |
| `PointType` | `account-service/.../enums/` | `EARN`, `CANCEL`, `EXPIRE` |
| `PointProcessStatus` | `account-service/.../enums/` | `READY`, `SUCCESS`, `FAILED` |
| `UserEntity.updatePointBalance()` | `account-service/.../entity/` | 잔액 갱신 메서드 |

`point_histories` 테이블 접근은 **account-service에서만** 수행한다.

---

## 5. core-service 공유 계약

### 5.1 이벤트 DTO

```java
// core-service/.../event/UserPointChangedEvent.java
public record UserPointChangedEvent(
    String eventId,
    String eventType,      // USER_POINT_CHANGED
    String eventVersion,   // v1
    Long userId,
    Long orderId,
    String pointType,      // EARN | CANCEL | EXPIRE
    Integer changeAmount,
    Integer balanceAfter,
    LocalDateTime occurredAt
) {}
```

- 포인트 금액은 **Integer** (DB `INT`와 정합)
- `orderId`는 `point_histories.order_id` FK용 필수값

### 5.2 상수

| 상수 클래스 | 값 |
|------------|-----|
| `EventLogTypes.USER_POINT_CHANGED` | `"USER_POINT_CHANGED"` |
| `EventVersions.V1` | `"v1"` |
| `RabbitMqKeys.UserPointChanged.EXCHANGE` | `user.events.v1` |
| `RabbitMqKeys.UserPointChanged.ROUTING_KEY` | `user.point.changed` |
| `RabbitMqKeys.UserPointChanged.QUEUE` | `account-service.user.point.changed.v1` |
| `RabbitMqKeys.UserPointChanged.DLQ` | `account-service.user.point.changed.v1.dlq` |

MQ 키·이벤트 타입·버전은 **core-service 상수만** 사용한다. 서비스별 하드코딩 금지.

### 5.3 이벤트 JSON 예시

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "USER_POINT_CHANGED",
  "eventVersion": "v1",
  "userId": 1,
  "orderId": 1,
  "pointType": "EARN",
  "changeAmount": 100,
  "balanceAfter": 1000,
  "occurredAt": "2026-06-05T12:00:00"
}
```

---

## 6. DB 반영 규칙

### 6.1 `users`

```sql
point_balance INT NOT NULL DEFAULT 0
```

- 갱신: 이벤트 `balanceAfter`를 **그대로 설정**
- 잔액 권위: **order-service(발행 측)** — account-service는 별도 검증 없이 반영

### 6.2 `point_histories`

| 컬럼 | 소비 시 값 |
|------|-----------|
| `user_id` | `event.userId` |
| `order_id` | `event.orderId` |
| `point_type` | `event.pointType` (enum name) |
| `point_amount` | `event.changeAmount` |
| `balance_after` | `event.balanceAfter` |
| `process_status` | `SUCCESS` |
| `event_id` | `event.eventId` (UNIQUE) |
| `processed_at` | 처리 시각 |

FK: `user_id → users.id`, `order_id → orders.id`

---

## 7. 버전 확장 규칙

새 버전 추가 시 아래 패턴을 **반드시** 따른다.

### 7.1 Handler 패턴

```java
// 인터페이스 — account-service/.../consumer/handler/
public interface UserPointChangedEventHandler {
    String supportedVersion();
    void handle(UserPointChangedEvent event);
}
```

- `UserPointChangedEventDispatcher`는 `List<UserPointChangedEventHandler>`를 `supportedVersion()` 기준 Map으로 자동 등록
- **Dispatcher에 if/switch 분기를 직접 추가하지 않는다**
- 새 버전 = 새 Handler Bean 추가 + (필요 시) ProcessService 메서드 추가

### 7.2 v2 추가 시 체크리스트

1. `EventVersions.V2` 상수 추가 (core-service)
2. v2 이벤트 DTO 정의 (필드 변경 시 별도 record)
3. `RabbitMqKeys`에 v2 exchange/queue 추가
4. `UserPointChangedV2EventHandler` Bean 추가
5. `UserPointChangedProcessService.processV2()` 또는 별도 서비스 추가
6. order-service Outbox 기록·발행에 v2 분기 추가

---

## 8. 필수 파일 구조

에이전트는 아래 구조를 유지한다. 파일명·패키지 변경 금지 (확장 시 추가만 허용).

### order-service

```
api/user/
  UserPointChangedOutboxController.java    # POST /api/outbox/user-point-changed
  UserPointChangedOutboxRequest.java
outbox/
  UserPointChangedOutboxService.java       # event_logs INSERT
publisher/
  EventLogPublishService.java              # 발행 오케스트레이션
  EventLogPublishTransactionService.java
  UserPointChangedEventPublisher.java      # RabbitMQ send
scheduler/
  EventLogPublishScheduler.java
```

### account-service

```
consumer/
  UserPointChangedConsumer.java
  UserPointChangedEventDispatcher.java
  handler/
    UserPointChangedEventHandler.java
    UserPointChangedV1EventHandler.java
  service/
    UserPointChangedProcessService.java
  exception/
    UnsupportedUserPointChangedVersionException.java
    UserPointChangedProcessException.java
entity/
  PointHistoryEntity.java
  UserEntity.java
enums/
  PointType.java
  PointProcessStatus.java
repository/
  PointHistoryRepository.java
```

### core-service

```
event/UserPointChangedEvent.java
keys/rabbitmq/RabbitMqKeys.java
keys/event/EventLogTypes.java
keys/event/EventVersions.java
config/RabbitMqConfig.java
```

### 테스트 UI

```
front/index.html    # Outbox record 테스트 (userId, orderId, pointType, changeAmount, balanceAfter, occurredAt)
```

---

## 9. 코딩 규칙

에이전트가 코드 작성 시 반드시 지킬 규칙:

1. **이벤트 계약 변경 시** `UserPointChangedEvent` + Outbox Request/Service + `front/index.html`을 **함께** 수정
2. **레이어 책임 분리** (섹션 2) — Consumer에 비즈니스 로직 넣지 않음
3. **멱등성** (섹션 4.2) — `event_id` 기준, 중복 시 예외 throw 금지
4. **포인트 타입 Integer** — `BigDecimal` 사용 금지
5. **MQ/이벤트 상수** — `RabbitMqKeys`, `EventLogTypes`, `EventVersions`만 사용
6. **캐시 무효화** — `users` 변경 후 `UserDetailCacheEvictEvent` 발행 필수
7. **기존 컨벤션 준수** — record DTO, `@RequiredArgsConstructor`, Lombok `@Builder`, `@Transactional`
8. **plan 우선** — plan에 없는 필드·테이블·흐름을 임의로 추가하지 않음

---

## 10. 설계 결정 사항

아래는 의도된 설계이며, 에이전트가 임의로 변경하지 않는다.

| 항목 | 결정 |
|------|------|
| 잔액 갱신 방식 | `balanceAfter` 직접 설정 (누적 계산 아님) |
| 잔액 권위 | order-service가 결정, account-service는 반영만 |
| `point_histories` 소유 | account-service가 직접 적재 |
| 버전 라우팅 | Handler Bean 자동 등록 (Dispatcher if/switch 금지) |
| claim 시 상태 | `READY` 최초 시도도 `RETRYING`으로 전환 |
| 중복 처리 | warn + skip (예외 throw 아님) |

---

## 11. 검증 기준

구현이 plan을 따르는지 아래로 확인한다.

### E2E 흐름

1. `front/index.html`에서 Outbox record 실행
2. `event_logs`에 `publish_status = READY` 생성 확인
3. 스케줄러 실행 후 `publish_status = PUBLISHED` 확인
4. account-service 로그: `UserPointChanged 이벤트 처리 완료`
5. `users.point_balance = balanceAfter` 확인
6. `point_histories`에 `event_id`, `process_status = SUCCESS` 행 확인

### 필수 동작

- [ ] 동일 `eventId` 재수신 시 중복 INSERT 없이 skip
- [ ] 존재하지 않는 `userId` 시 예외 throw
- [ ] 미지원 `eventVersion` 시 `UnsupportedUserPointChangedVersionException`
- [ ] 포인트 변경 후 Redis 사용자 캐시 무효화

### 사전 조건

- DB에 `users(id)`, `orders(id)` FK 대상 데이터 존재

---

## 변경 이력

| 일자 | 내용 |
|------|------|
| 2026-06-05 | 최초 작성 |
| 2026-06-05 | 후속 작업 로드맵 제거, 코드 구현 기준서로 재정의 |
