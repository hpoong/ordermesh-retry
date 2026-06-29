# Phase 1: recovery-service 기반 + failed-messages 적재 API

> **선행:** [v2-migration Phase 3](../v2-migration/phase-03-processing-service.md) (processing v2 Consumer·오케스트레이션)  
> **후행:** [Phase 2](phase-02-dlq-and-processing-failure-handling.md)  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 8, [failed_messages 스키마](../../../core-service/src/main/resources/db/migration/V1__create_tables.sql)

---

## 목적

recovery-service를 **독립 기동 가능한 상태**로 만들고, `failed_messages` 적재·조회 Internal API를 구현한다.

이 Phase에서는 processing 연동·DLQ·재처리는 **범위 밖**이다. 수동 HTTP 호출로 적재 API를 검증한다.

---

## 전제조건

- [ ] PostgreSQL docker-compose 기동 가능 (processing과 동일 DB)
- [ ] `failed_messages` 테이블이 Flyway V1로 생성되어 있음
- [ ] [recovery-init README](README.md) 아키텍처·소유권 원칙 숙지

---

## 작업 목록

### 1. `build.gradle` 의존성 추가

**파일:** `recovery-service/build.gradle`

| 의존성 | 용도 |
|--------|------|
| `runtimeOnly 'org.postgresql:postgresql'` | DB 연결 |
| `implementation 'org.springframework.boot:spring-boot-starter-amqp'` | Phase 2 DLQ Consumer 선행 준비 (선택: Phase 2에서 추가) |

`processing-service/build.gradle`과 동일한 Spring Boot·JPA 패턴을 따른다.

### 2. `application.yml` 설정

**파일:** `recovery-service/src/main/resources/application.yml`

| 설정 | 예시 |
|------|------|
| `server.port` | `9300` (기존 유지) |
| `spring.datasource.*` | processing과 동일 PostgreSQL |
| `spring.jpa.*` | `ddl-auto: validate`, `open-in-view: false` |
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

공통 적재 로직 (Phase 2 DLQ Consumer에서도 재사용):

1. `(consumer_name, event_id)` UK 기준 존재 여부 확인
2. 신규 → INSERT (`reprocess_status = WAITING`, `dlq_stored_yn = N`)
3. 기존 → `failure_reason`, `retry_count`, `last_failed_at` 갱신 (멱등)

### 7. Internal API

**패키지:** `recovery-service/src/main/java/com/hopoong/recovery/api/internal/`

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/internal/v1/failed-messages` | 실패 메시지 적재 (멱등) |
| `GET` | `/internal/v1/failed-messages` | 목록 (`reprocess_status`, `failure_type` 필터) |
| `GET` | `/internal/v1/failed-messages/{id}` | 상세 조회 |

**POST 요청 필드 (제안):**

| 필드 | 필수 | 설명 |
|------|------|------|
| `eventId` | Y | 이벤트 ID |
| `consumerName` | Y | Consumer 이름 |
| `queueName` | Y | 수신 큐 |
| `exchangeName` | Y | Exchange |
| `routingKey` | Y | Routing key |
| `payload` | Y | 원본 JSON |
| `failureType` | Y | `BUSINESS` / `SYSTEM` / `TIMEOUT` |
| `failureReason` | Y | 실패 상세 |
| `retryCount` | N | 기본 0 |
| `maxRetryCount` | N | 기본 3 |

응답 형식은 `CommonResponse` / `SuccessResponse` (core-service) 패턴을 따른다.

### 8. docker-compose

**파일:** `docker-compose/docker-compose.yml`

- `recovery-service` 컨테이너 추가 (port 9300)
- `depends_on`: postgres, rabbitmq (Phase 2 전 rabbitmq는 선택)

---

## 패키지 구조 (Phase 1 범위)

```
recovery-service/src/main/java/com/hopoong/recovery/
├── RecoveryServiceApplication.java
├── config/
│   └── RecoveryProperties.java          # max-retry-count 등
├── enums/
│   ├── FailureType.java
│   └── ReprocessStatus.java
├── entity/
│   └── FailedMessage.java
├── repository/
│   └── FailedMessageRepository.java
├── ingest/
│   └── FailedMessageIngestService.java
└── api/internal/
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
| **금지** | `core-service/RabbitMqConfig` DLQ Bean (Phase 2) |

---

## 검증 체크리스트

### 기동

- [ ] recovery-service 기동 (port 9300)
- [ ] PostgreSQL 연결·JPA `failed_messages` 매핑 정상

### API (수동)

1. [ ] `POST /internal/v1/failed-messages` — 신규 INSERT, `reprocess_status = WAITING`
2. [ ] 동일 `(consumer_name, event_id)` 재요청 — 멱등 upsert (UK 위반 없음)
3. [ ] `GET /internal/v1/failed-messages` — 목록·필터 조회
4. [ ] `GET /internal/v1/failed-messages/{id}` — 상세 조회

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
feat(recovery-service): [recovery] failed-messages 적재 API 및 기반 구축
```

---

## 다음 단계

[Phase 2: DLQ + processing 실패 분기](phase-02-dlq-and-processing-failure-handling.md)
