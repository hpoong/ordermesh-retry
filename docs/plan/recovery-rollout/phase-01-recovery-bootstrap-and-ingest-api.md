# Phase 1: recovery-service 기반 + ingest 서비스

> **선행:** [v2-migration Phase 3](../v2-migration/phase-03-processing-service.md) (processing v2 Consumer·오케스트레이션)  
> **후행:** [Phase 2](phase-02-dlq-and-processing-failure-handling.md)  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 8, [failed_messages 스키마](../../../core-service/src/main/resources/db/migration/V1__create_tables.sql)

---

## 목적

recovery-service를 **독립 기동 가능한 상태**로 만들고, `FailedMessageIngestService`와 **운영 조회용 Internal API**를 구현한다.

런타임 실패 적재는 Phase 2에서 **MQ Consumer**가 담당한다. Phase 1에서는 processing 연동·MQ·재처리는 **범위 밖**이다.

---

## 전제조건

- [ ] PostgreSQL docker-compose 기동 가능 (processing과 동일 DB)
- [ ] `failed_messages` 테이블이 Flyway V1로 생성되어 있음
- [ ] [recovery-rollout README](README.md) MQ 단일 경로·소유권 원칙 숙지

---

## 작업 목록

### 1. `build.gradle` 의존성 추가

**파일:** `recovery-service/build.gradle`

| 의존성 | 용도 |
|--------|------|
| `runtimeOnly 'org.postgresql:postgresql'` | DB 연결 |
| `implementation 'org.springframework.boot:spring-boot-starter-amqp'` | Phase 2 Ingest Consumer 선행 준비 |

`processing-service/build.gradle`과 동일한 Spring Boot·JPA 패턴을 따른다.

### 2. `application.yml` 설정

**파일:** `recovery-service/src/main/resources/application.yml`

| 설정 | 예시 |
|------|------|
| `server.port` | `9300` (기존 유지) |
| `spring.datasource.*` | processing과 동일 PostgreSQL |
| `spring.jpa.*` | `ddl-auto: validate`, `open-in-view: false` |
| `spring.rabbitmq.*` | localhost (Phase 2 Consumer 선행) |
| `app.recovery.max-retry-count` | `3` (스키마 DEFAULT와 일치) |

### 3. Enum 추가

**패키지:** `recovery-service/src/main/java/com/hopoong/recovery/enums/`

| Enum | 값 | 용도 |
|------|-----|------|
| `FailureType` | `BUSINESS`, `SYSTEM`, `TIMEOUT` | `failure_type` 컬럼 |
| `ReprocessStatus` | `WAITING`, `PROCESSING`, `SUCCESS`, `FAILED` | `reprocess_status` 컬럼 |

다른 서비스 enum과 동일하게 `koreanName` + `getKoreanName()` 패턴 적용 (선택).

### 4. `FailedMessage` 엔티티 보강

**파일:** `recovery-service/src/main/java/com/hopoong/recovery/entity/FailedMessage.java`

- 기존 JPA 매핑 유지
- (선택) `markDlqStored()`, `claimForReprocess()` 등 도메인 메서드 추가

### 5. Repository

**파일:** `recovery-service/src/main/java/com/hopoong/recovery/repository/FailedMessageRepository.java`

| 메서드 | 용도 |
|--------|------|
| `findByConsumerNameAndEventId` | 멱등 upsert 조회 |
| `findByReprocessStatus` | 목록 조회 (Phase 3 선행) |
| `existsByConsumerNameAndEventId` | 중복 확인 |

### 6. `FailedMessageIngestService`

**패키지:** `recovery-service/src/main/java/com/hopoong/recovery/ingest/`

Phase 2 MQ Consumer와 Phase 1 수동 검증에서 **공통 사용**:

1. `(consumer_name, event_id)` UK 기준 존재 여부 확인
2. 신규 → INSERT (`reprocess_status = WAITING`, `dlq_stored_yn`은 출처에 따라 설정)
3. 기존 → `failure_reason`, `retry_count`, `last_failed_at` 갱신 (멱등)

입력: `FailedMessageIngestCommand` (eventId, consumerName, queueName, exchangeName, routingKey, payload, failureType, failureReason, retryCount, dlqStoredYn)

### 7. Internal API — 조회·수동 적재 (운영·E2E용)

> **런타임 경로는 MQ**이다. POST API는 E2E·운영 수동 적재용으로만 둔다. processing에서 HTTP 호출 **금지**.

**패키지:** `recovery-service/src/main/java/com/hopoong/recovery/api/internal/`

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/internal/v1/failed-messages` | (선택) 수동 적재 — E2E·운영용 |
| `GET` | `/internal/v1/failed-messages` | 목록 (`reprocess_status`, `failure_type` 필터) |
| `GET` | `/internal/v1/failed-messages/{id}` | 상세 조회 |

응답 형식은 `CommonResponse` / `SuccessResponse` (core-service) 패턴을 따른다.

### 8. docker-compose

**파일:** `docker-compose/docker-compose.yml`

- `recovery-service` 컨테이너 추가 (port 9300)
- `depends_on`: postgres, rabbitmq

---

## 패키지 구조 (Phase 1 범위)

```
recovery-service/src/main/java/com/hopoong/recovery/
├── RecoveryServiceApplication.java
├── config/
│   └── RecoveryProperties.java
├── enums/
│   ├── FailureType.java
│   └── ReprocessStatus.java
├── entity/
│   └── FailedMessage.java
├── repository/
│   └── FailedMessageRepository.java
├── ingest/
│   ├── FailedMessageIngestService.java
│   └── FailedMessageIngestCommand.java
└── api/internal/                          # 조회·수동 적재 (운영용)
    ├── FailedMessageInternalController.java
    └── dto/
        ├── FailedMessageCreateRequest.java
        └── FailedMessageResponse.java
```

---

## 파일 변경 요약

| 구분 | 범위 |
|------|------|
| **신규** | `recovery-service/**` config, enum, repository, ingest, api |
| **수정** | `recovery-service/build.gradle`, `application.yml` |
| **수정** | `docker-compose/docker-compose.yml` |
| **금지** | `processing-service/**` (Phase 2) |
| **금지** | ingest 큐 Bean·Consumer (Phase 2) |

---

## 검증 체크리스트

### 기동

- [ ] recovery-service 기동 (port 9300)
- [ ] PostgreSQL 연결·JPA `failed_messages` 매핑 정상

### IngestService (단위·수동)

1. [ ] `FailedMessageIngestService.ingest()` — 신규 INSERT, `reprocess_status = WAITING`
2. [ ] 동일 `(consumer_name, event_id)` 재호출 — 멱등 upsert (UK 위반 없음)

### API (운영·E2E)

1. [ ] `GET /internal/v1/failed-messages` — 목록·필터 조회
2. [ ] `GET /internal/v1/failed-messages/{id}` — 상세 조회
3. [ ] (선택) `POST /internal/v1/failed-messages` — 수동 적재

### DB

- [ ] `failed_messages` 행이 스키마 제약(UK, NOT NULL)을 만족
- [ ] `payload`가 JSONB로 저장됨

---

## 롤백

| 조치 | 방법 |
|------|------|
| recovery 미배포 | recovery-service 기동 중지 — processing·order 영향 없음 |
| 코드 revert | recovery-service 신규 패키지 revert |
| DB | 개발 환경 `failed_messages` 테스트 행 DELETE |

---

## 커밋 메시지 예시

```text
feat(recovery-service): [recovery] failed-messages ingest 서비스 및 조회 API
```

---

## 다음 단계

[Phase 2: MQ 실패 전달 + processing 실패 분기](phase-02-dlq-and-processing-failure-handling.md)
