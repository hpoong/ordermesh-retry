# Phase 1: core MQ + order v2 발행

> **선행:** 없음  
> **후행:** [Phase 2](phase-02-account-internal-api.md)  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 4, 7

---

## 목적

`UserPointChanged` **v2 MQ 계약**을 core-service에 정의하고, order-service가 **v2로만 발행**하도록 전환한다.

v1 account 큐·fan-out(`PROCESSING_QUEUE` + 동일 routing key) 패턴은 **사용하지 않는다.**

---

## 전제조건

- [ ] RabbitMQ·PostgreSQL docker-compose 기동 가능
- [ ] [v2_plan.md](../v2_plan.md) v2 MQ 계약 숙지
- [ ] (선택) v1 `event_logs` in-flight 건 처리 방침 확정

---

## v2 MQ·이벤트 계약

| 상수 | v2 값 |
|------|-------|
| `EXCHANGE` | `user.events` |
| `ROUTING_KEY` | `user.point.changed` |
| `QUEUE` | `processing-service.user.point.changed.v2` |
| `DLQ` | `processing-service.user.point.changed.v2.dlq` |
| `eventVersion` | `v2` (`EventVersions.V2`) |

---

## 작업 목록

### 0. (선택) 기존 fan-out 코드 revert

이전 `phase-01-mq-infrastructure` fan-out 구현이 있다면 **먼저 제거**:

- `RabbitMqKeys.UserPointChangedV2.PROCESSING_QUEUE`, `PROCESSING_DLQ`
- `RabbitMqConfig.userPointChangedProcessingQueue()`, `userPointChangedProcessingBinding()`
- v1 `userPointChangedQueue()`, `userPointChangedBinding()` — v2 전환 시 **함께 제거** (Phase 4에서 MQ UI 정리)

### 1. `EventVersions.V2` 추가

**파일:** `core-service/src/main/java/com/hopoong/core/keys/event/EventVersions.java`

```java
public static final String V2 = "v2";
```

### 2. `RabbitMqKeys` v2 단일 상수

**파일:** `core-service/src/main/java/com/hopoong/core/keys/rabbitmq/RabbitMqKeys.java`

```java
public static final class UserPointChangedV2 {

    public static final String EXCHANGE = "user.events";
    public static final String ROUTING_KEY = "user.point.changed";
    public static final String QUEUE = "processing-service.user.point.changed.v2";
    public static final String DLQ = QUEUE + ".dlq";

    private UserPointChangedV2() { }
}
```

- `PROCESSING_QUEUE` 이중 상수 패턴 **사용 금지**
- v1 상수(`user.events.v1`, `account-service...`)는 Phase 4까지 코드에 남길지, Phase 1에서 즉시 제거할지 팀 선택 — **권장: Phase 1에서 v2만 남김**

### 3. `RabbitMqConfig` v2 Bean

**파일:** `core-service/src/main/java/com/hopoong/core/config/RabbitMqConfig.java`

| Bean | 설명 |
|------|------|
| `userPointChangedExchange()` | `user.events` TopicExchange (durable) |
| `userPointChangedQueue()` | `processing-service.user.point.changed.v2` durable queue |
| `userPointChangedBinding()` | exchange + `user.point.changed` routing key |
| (선택) `userPointChangedDlq()` | DLQ queue — 정책 확정 후 |

**제거 대상 (v1 / fan-out):**

- `userPointChangedProcessingQueue()`, `userPointChangedProcessingBinding()`
- v1 account queue·binding Bean (있다면)

### 4. order-service Outbox v2 발행

**파일:** `order-service/.../outbox/UserPointChangedOutboxService.java`

| 변경 | 내용 |
|------|------|
| `UserPointChangedEvent` | `EventVersions.V2` |
| `EventLog.eventVersion` | `EventVersions.V2` |
| `EventLog.exchangeName` | `RabbitMqKeys.UserPointChangedV2.EXCHANGE` |
| `EventLog.routingKey` | `RabbitMqKeys.UserPointChangedV2.ROUTING_KEY` |

`UserPointChangedEventPublisher` — 변경 없음 (`eventLog`의 exchange·routing key 사용).

### 5. in-flight Outbox 처리

**대상:** `event_logs` where `event_version = 'v1'` OR `routing_key = 'user.point.changed'` AND `publish_status IN ('READY', 'RETRYING')`

| 환경 | 조치 |
|------|------|
| 로컬 | SQL purge 또는 수동 v2 재기록 |
| 운영 | v2 배포 전 drain 완료 확인 |

### 6. 서비스 재기동

- order-service 재기동 (core-service 의존)
- RabbitMQ Management UI에서 v2 큐·binding 확인

---

## 파일 변경 요약

| 구분 | 파일 |
|------|------|
| **수정** | `core-service/.../keys/event/EventVersions.java` |
| **수정** | `core-service/.../keys/rabbitmq/RabbitMqKeys.java` |
| **수정** | `core-service/.../config/RabbitMqConfig.java` |
| **수정** | `order-service/.../outbox/UserPointChangedOutboxService.java` |
| **금지** | `account-service/**` (Phase 2) |
| **금지** | `processing-service/**` Consumer (Phase 3) |

---

## 검증 체크리스트

### RabbitMQ

- [ ] exchange `user.events` 존재
- [ ] 큐 `processing-service.user.point.changed.v2` 존재
- [ ] `user.point.changed` routing key로 binding 확인
- [ ] (Phase 4 전) v1 큐 `account-service.user.point.changed.v1` — **신규 v2 메시지 미유입** 확인

### order 발행

1. [ ] `front/index.html` Outbox record
2. [ ] `event_logs`: `event_version = v2`, `routing_key = user.point.changed`, `publish_status = PUBLISHED`
3. [ ] RabbitMQ processing v2 큐에 메시지 Ready (Consumer 없음 → 정상)

### v1 격리

- [ ] v1 account Consumer 기동 중이어도 **v2 메시지 미수신** (routing key 불일치)
- [ ] v1 `event_logs` 미처리 건이 v2 발행을 막지 않음

---

## 롤백

| 조치 | 방법 |
|------|------|
| 코드 | Phase 1 commit revert |
| MQ | v2 큐·binding RabbitMQ UI에서 삭제 (선택) |
| order | v1 Outbox·MQ key 복구 시 v1 routing key로 재발행 |

---

## 커밋 메시지 예시

```text
feat(core-service): [key] UserPointChanged v2 MQ 계약 및 order v2 발행
```

---

## 다음 단계

[Phase 2: account internal API](phase-02-account-internal-api.md)
