# Phase 2: account internal API

> **선행:** [Phase 1](phase-01-core-and-order-v2.md) (v2 MQ·order v2 발행 완료)  
> **후행:** [Phase 3](phase-03-processing-service.md)  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 6

---

## 목적

processing-service가 호출할 **잔액 반영 Internal API**를 추가한다.

멱등은 **processing** `point_histories`·`message_process_logs`에서 처리한다. account는 `balanceAfter` **직접 설정**만 수행한다.

v1 MQ Consumer(`consumer/user/point/*`)는 **이 Phase에서 삭제하지 않는다** — v2 routing key 분리로 v2 메시지를 수신하지 않으며, Phase 4에서 코드 제거한다.

---

## 전제조건

- [ ] Phase 1 완료 (v2 exchange·queue·order v2 발행)
- [ ] account-service 로컬 기동 가능 (port 9000)

---

## v1 Consumer와의 관계

| 항목 | Phase 2 동작 |
|------|--------------|
| v1 Consumer 코드 | **잔존** (Phase 4 제거) |
| v2 MQ 메시지 | v1 Consumer **미수신** (`user.point.changed` ≠ `user.point.changed.v2`) |
| v1 E2E | v1 `event_logs` + v1 routing key로만 가능 — **신규 Outbox는 v2만** |

---

## 작업 목록

### 1. Request DTO (신규)

**패키지:** `account-service/.../api/internal/user/`

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

- `eventId`는 로그·추적용. account 측 멱등 저장소는 **사용하지 않는다.**

### 2. Service — `UserPointApplyService` (신규)

**파일:** `api/internal/user/UserPointApplyService.java`

처리 순서 ([v2_plan.md](../v2_plan.md) 섹션 6.3):

1. Request 필수값 검증 → `CoreException.badRequest(ACCOUNT_USERS, ...)`
2. `userRepository.findByIdForUpdate(userId)` → 없으면 `CoreException.notFound`
3. `userEntity.updatePointBalance(balanceAfter)` — 직접 설정, 누적 계산 아님
4. `applicationEventPublisher.publishEvent(new UserDetailCacheEvictEvent(userId))`
5. `200` 반환

**재사용 (기존 코드):**

- `UserRepository.findByIdForUpdate`
- `UserEntity.updatePointBalance`
- `UserDetailCacheEvictEvent` + `UserDetailCacheEvictListener`

**예외:** [exception-handling-convention.md](../../convention/exception-handling-convention.md) — Internal API는 HTTP 경계이므로 `CoreException` 사용.

### 3. Controller (신규)

**파일:** `api/internal/user/UserPointChangedInternalController.java`

```
POST /internal/v1/users/point-changed
Content-Type: application/json
Body: UserPointChangedApplyRequest
Response: 200 OK (SuccessResponse 또는 void)
```

- `@RestController` + `@RequestMapping("/internal/v1/users")`
- processing만 호출 — 인증/네트워크 제한은 후속 (로컬에서는 localhost만)

### 4. (선택) CommonResponseCodeEnum

internal API 전용 코드가 필요하면 `core-service`에 enum 추가. 없으면 `ACCOUNT_USERS` 재사용.

---

## 파일 변경 요약

| 구분 | 파일 |
|------|------|
| **신규** | `api/internal/user/UserPointChangedInternalController` |
| **신규** | `api/internal/user/UserPointChangedApplyRequest` |
| **신규** | `api/internal/user/UserPointApplyService` |
| **금지** | `consumer/user/point/**` 삭제 (Phase 4) |
| **금지** | `entity/PointHistoryEntity.java` 삭제 (Phase 4) |
| **금지** | Flyway·`user_point_applied_events` 관련 코드 추가 |

---

## 검증 체크리스트

### Internal API (curl)

```bash
curl -X POST http://localhost:9000/internal/v1/users/point-changed \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "test-event-001",
    "userId": 1,
    "orderId": 1,
    "pointType": "EARN",
    "changeAmount": 100,
    "balanceAfter": 1000,
    "occurredAt": "2026-06-06T12:00:00"
  }'
```

- [ ] 최초 호출: `users.point_balance = 1000`
- [ ] 존재하지 않는 `userId`: 404
- [ ] Redis 사용자 캐시 evict 확인 (`getUser` 시 DB 값 반영)
- [ ] 동일 요청 재호출: `users.point_balance` 동일 값 유지 (자연 멱등)

### REST 회귀

- [ ] REST `/api/v1/users` 정상
- [ ] v2 Outbox 발행 시 v1 Consumer 로그에 **수신 없음** (routing key 분리)

---

## 선행 구현 정리 (참고)

이전 plan 기준으로 Phase 2를 이미 구현했다면 **제거 대상**:

- `core-service/.../migration/V4__create_user_point_applied_events.sql`
- `UserPointAppliedEventEntity`, `UserPointAppliedEventRepository`
- `UserPointApplyService` 내 `existsByEventId`·INSERT·`DataIntegrityViolationException` 멱등 분기

DB에 V4가 적용됐으면 개발 환경에서 `user_point_applied_events` 테이블 DROP.

---

## 롤백

| 조치 | 방법 |
|------|------|
| 코드 | internal 패키지 revert |
| v2 발행 | Phase 1 revert 시 v2 MQ·Outbox 복구 |

---

## 커밋 메시지 예시

```text
feat(account-service): [point] UserPointChanged internal API 추가
```

---

## 다음 단계

[Phase 3: processing-service 구현](phase-03-processing-service.md)
