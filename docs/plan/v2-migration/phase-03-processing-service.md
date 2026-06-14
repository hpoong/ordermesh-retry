# Phase 3: processing-service 구현

> **선행:** [Phase 1](phase-01-core-and-order-v2.md), [Phase 2](phase-02-account-internal-api.md)  
> **후행:** [Phase 4](phase-04-legacy-cleanup.md)  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 5, 11

---

## 목적

processing-service에 `UserPointChanged` **v2 소비·오케스트레이션** 파이프라인을 구현한다.

account internal API를 호출해 잔액을 반영하고, `point_histories`·`message_process_logs`를 적재한다.

v2 routing key 분리로 **이중 소비 없이** account v1 Consumer와 processing v2 Consumer를 동시에 기동해도 v2 E2E 검증이 가능하다 (v1 Consumer는 v2 메시지 미수신).

---

## 전제조건

- [ ] Phase 1: v2 exchange·queue·binding, order v2 발행
- [ ] Phase 2: `POST /internal/v1/users/point-changed` 단독 검증 완료
- [ ] account `consumer/user/point/*` 구조 참고 가능 (복사·참조용, Phase 4에서 제거)

---

## 작업 목록

### 1. `build.gradle` 의존성 추가

**파일:** `processing-service/build.gradle`

```gradle
implementation 'org.springframework.boot:spring-boot-starter-amqp'
runtimeOnly 'org.postgresql:postgresql'
```

- `spring-boot-starter-web` — 유지 (HTTP client + health)
- account-service와 동일한 Spring Boot·Java 17 버전 유지

### 2. `application.yml` 설정

**파일:** `processing-service/src/main/resources/application.yml`

| 키 | 예시 값 |
|----|---------|
| `server.port` | `9200` |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/ordermesh-retry` |
| `spring.rabbitmq.*` | order와 동일 (host, port, vhost) |
| `app.account.base-url` | `http://localhost:9000` |

로컬 프로파일 필요 시 `application-local.yml` 추가.

### 3. 패키지 구조 (신규)

```
processing-service/src/main/java/com/hopoong/processing/
├── consumer/
│   └── user/point/
│       ├── UserPointChangedConsumer.java
│       ├── UserPointChangedEventDispatcher.java
│       ├── handler/
│       │   ├── UserPointChangedEventHandler.java
│       │   └── UserPointChangedV2EventHandler.java
│       ├── service/
│       │   └── UserPointChangedProcessService.java
│       ├── client/
│       │   └── AccountPointApplyClient.java
│       └── exception/
│           ├── UserPointChangedProcessException.java
│           └── UnsupportedUserPointChangedVersionException.java
├── entity/
│   ├── PointHistory.java
│   └── MessageProcessLog.java
├── enums/
│   ├── PointType.java
│   ├── PointProcessStatus.java
│   └── MessageProcessStatus.java
└── repository/
    ├── PointHistoryRepository.java
    └── MessageProcessLogRepository.java
```

### 4. Consumer

**파일:** `UserPointChangedConsumer.java`

```java
@RabbitListener(queues = RabbitMqKeys.UserPointChangedV2.QUEUE)
public void consumeUserPointChanged(UserPointChangedEvent message) { ... }
```

- `QUEUE` = `processing-service.user.point.changed.v2`
- `PROCESSING_QUEUE` 상수 **사용 금지**

### 5. Dispatcher·Handler

account-service `consumer/user/point/` 구조를 **참고해 processing에 신규 작성**:

- `UserPointChangedEventDispatcher` — `eventType` 검증, Handler Map 라우팅
- `UserPointChangedV2EventHandler` — `EventVersions.V2` 지원, `processV2` 위임
- Dispatcher에 if/switch 버전 분기 **금지** (Handler Bean 자동 등록)

### 6. ProcessService

**파일:** `UserPointChangedProcessService.java`

처리 순서 ([v2_plan.md](../v2_plan.md) 섹션 5.3):

**account API 호출 전 processing 멱등 검사(3~4단계)를 반드시 완료한다.**

1. payload·`pointType` 검증
2. `message_process_logs` `RECEIVED`
3. `event_id` 멱등 검사 → 중복 시 `DUPLICATE`, ack (**account API 미호출**)
4. `PROCESSING` 전이
5. `AccountPointApplyClient.apply(event)`
6. `point_histories` INSERT (`SUCCESS`)
7. `message_process_logs` `SUCCESS` + `acked_at`

**예외:** 비REST — `UserPointChangedProcessException`, `UnsupportedUserPointChangedVersionException`. `CoreException` **사용 금지**.

### 7. AccountPointApplyClient

**파일:** `client/AccountPointApplyClient.java`

- `RestClient` 또는 `RestTemplate`
- `POST {baseUrl}/internal/v1/users/point-changed`
- body: `UserPointChangedApplyRequest` (processing 측 record 또는 core DTO)
- 2xx → 성공, 404 → 복구 불가, 5xx/timeout → retry 대상 예외

### 8. Repository·Enum

| Repository | 메서드 |
|------------|--------|
| `PointHistoryRepository` | `existsByEventId`, `save` |
| `MessageProcessLogRepository` | 상태별 save·조회 |

| Enum | 값 |
|------|-----|
| `MessageProcessStatus` | `RECEIVED`, `PROCESSING`, `SUCCESS`, `FAILED`, `DUPLICATE`, `RETRY`, `DLQ` |
| `PointProcessStatus` | `SUCCESS`, `FAILED` (point_histories) |
| `PointType` | `EARN`, `CANCEL`, `EXPIRE` |

### 9. (선택) RabbitListenerErrorHandler

retry/DLQ 정책 명시 — v2_plan 섹션 5.5. 최소 구현 시 Spring AMQP 기본 retry로 시작 가능.

---

## 파일 변경 요약

| 구분 | 범위 |
|------|------|
| **신규** | `processing-service/**` consumer·service·client·repository·enums |
| **수정** | `processing-service/build.gradle`, `application.yml` |
| **참고만** | `account-service/consumer/user/point/**` (삭제는 Phase 4) |
| **금지** | `order-service/**` (Phase 1에서 완료) |
| **금지** | account `consumer/user/point/**` 삭제 (Phase 4) |

---

## 검증 체크리스트

### 기동

- [ ] processing-service 기동 (port 9200)
- [ ] account internal API 기동 (port 9000)
- [ ] order-service v2 발행 기동

### E2E (v2 단일 경로)

1. [ ] `front/index.html` → Outbox record
2. [ ] order `event_logs`: `event_version = v2`, `routing_key = user.point.changed.v2`, `PUBLISHED`
3. [ ] processing 로그: `UserPointChanged 이벤트 수신·처리 완료`
4. [ ] `message_process_logs`: `RECEIVED` → `PROCESSING` → `SUCCESS`
5. [ ] `users.point_balance = balanceAfter`
6. [ ] `point_histories`에 `process_status = SUCCESS` (processing 소유)
7. [ ] Redis 캐시 무효화

### 멱등·실패

- [ ] 동일 `eventId` 재발행/재수신 → processing `DUPLICATE` skip, account API 미중복 호출
- [ ] 없는 `userId` → processing `FAILED` 로그
- [ ] account 성공 후 `point_histories` 실패 시뮬레이션 → 재시도 시 `balanceAfter` 재설정 + point_histories 복구

### v1 격리

- [ ] v1 account Consumer 로그에 v2 Outbox 메시지 **수신 없음**
- [ ] `point_histories` 신규 행이 **processing 경로**로만 생성됨

---

## 롤백

| 조치 | 방법 |
|------|------|
| processing 미배포 | processing-service 기동 중지 — v2 메시지는 큐에 적재만 됨 |
| 코드 revert | processing-service 신규 패키지 revert |
| MQ | processing v2 큐 메시지 purge (개발 환경) |

---

## 커밋 메시지 예시

```text
feat(processing-service): [point] UserPointChanged v2 소비·오케스트레이션 구현
```

---

## 다음 단계

[Phase 4: legacy cleanup](phase-04-legacy-cleanup.md)
