# Phase 4: legacy cleanup (v1 레거시 정리)

> **선행:** [Phase 1](phase-01-core-and-order-v2.md) ~ [Phase 3](phase-03-processing-service.md) 완료, v2 E2E 검증  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 11, 12

---

## 목적

v1 account Consumer·중복 엔티티·v1 MQ 리소스를 제거하고, 문서를 v2 기준으로 통일한다.

**Phase 3 E2E 완료 후** 실행한다. v2가 유일 처리 경로임을 확인한 뒤 dead code를 삭제한다.

---

## 전제조건

- [ ] Phase 3 v2 E2E·멱등·실패 시나리오 검증 완료
- [ ] v2 단일 경로로 `UserPointChanged` 운영 가능 확인
- [ ] (권장) `git tag v2-pre-cleanup` 생성

---

## 작업 목록

### 1. account-service — v1 Consumer 제거

**삭제 대상 패키지·파일:**

```
account-service/src/main/java/com/hopoong/account/consumer/user/point/
├── UserPointChangedConsumer.java
├── UserPointChangedEventDispatcher.java
├── handler/
│   ├── UserPointChangedEventHandler.java
│   └── UserPointChangedV1EventHandler.java
├── service/
│   └── UserPointChangedProcessService.java
└── exception/
    ├── UserPointChangedProcessException.java
    └── UnsupportedUserPointChangedVersionException.java
```

### 2. account-service — point_histories 제거

v2에서 `point_histories` 소유는 processing-service.

| 삭제 | 파일 |
|------|------|
| Entity | `entity/PointHistoryEntity.java` |
| Repository | `repository/PointHistoryRepository.java` |

**주의:** DB 테이블 `point_histories`는 **삭제하지 않는다** — processing이 계속 사용.

### 2-1. (선행 Phase 2 구현 잔존 시) `user_point_applied_events` 제거

이전 plan 기준으로 구현된 항목이 있으면 **삭제**:

| 삭제 | 파일 |
|------|------|
| Flyway | `core-service/.../migration/V4__create_user_point_applied_events.sql` |
| Entity | `entity/UserPointAppliedEventEntity.java` |
| Repository | `repository/UserPointAppliedEventRepository.java` |

**DB:** 개발 환경에서 `user_point_applied_events` 테이블 DROP (V4 적용된 경우)

`UserPointApplyService`에서 `existsByEventId`·INSERT·멱등 분기 제거 — [v2_plan.md](../v2_plan.md) 섹션 6.3 순서로 단순화.

### 3. account-service — enum 정리

processing으로 이전 완료 후 account에서 제거:

- `enums/PointType.java` — account REST에서 미사용 시 삭제
- `enums/PointProcessStatus.java` — account에서 미사용 시 삭제

**검증:** `grep`으로 account-service 내 참조 0건 확인 후 삭제.

### 4. account-service — build.gradle

`spring-boot-starter-amqp`가 **internal API만** 있고 Consumer가 없으면:

- AMQP 의존성 제거 검토 (다른 Listener 없을 경우)
- 제거 시 `RabbitMqConfig` 빈 로딩 여부 확인

### 5. core-service — v1 MQ 리소스 정리

Phase 1에서 v2만 남겼다면 추가 작업 없을 수 있음. v1 상수·Bean 잔존 시:

| 작업 | 파일 |
|------|------|
| v1 queue·binding Bean 제거 | `RabbitMqConfig` |
| v1 상수 제거 | `RabbitMqKeys` — `user.events.v1`, `account-service.user.point.changed.v1` 등 |
| fan-out 잔존 제거 | `PROCESSING_QUEUE`, `PROCESSING_DLQ` |

**유지:** v2 `EXCHANGE`, `ROUTING_KEY`, `QUEUE`, `DLQ`

### 6. RabbitMQ 인프라 정리

RabbitMQ Management UI 또는 CLI:

| 삭제 대상 | 설명 |
|-----------|------|
| 큐 | `account-service.user.point.changed.v1` |
| DLQ | `account-service.user.point.changed.v1.dlq` |
| binding | `user.events.v1` ↔ v1 account queue |
| (선택) exchange | `user.events.v1` — 다른 이벤트·큐 없으면 삭제 |

**유지:** `user.events`, `processing-service.user.point.changed.v2` 및 binding

### 7. 문서 갱신

| 문서 | 내용 |
|------|------|
| [docs/process/account-service.md](../../services/account-service.md) | v2 역할 (internal API, Consumer 없음) |
| [docs/process/processing-service.md](../../services/processing-service.md) | v2 오케스트레이션 |
| [docs/db/tables.md](../../db/tables.md) | `point_histories` → processing 소유 |
| [docs/process/core-service.md](../../services/core-service.md) | v2 MQ key |
| [plan.md](../plan.md) | v1 레거시 표시, v2_plan 링크 |

### 8. 범위 외 (후속 스프린트)

- **recovery-service** `failed_messages` 연동 — [v2_plan.md](../v2_plan.md) 섹션 8
- internal API 인증 (API key, mTLS)
- `message_process_logs` 운영 대시보드

---

## 파일 변경 요약

| 구분 | 작업 |
|------|------|
| **삭제** | account `consumer/user/point/**` |
| **삭제** | account `PointHistoryEntity`, `PointHistoryRepository` |
| **선택 삭제** | `V4__create_user_point_applied_events.sql`, `UserPointAppliedEvent*` (선행 구현 잔존 시) |
| **선택 삭제** | account `PointType`, `PointProcessStatus` |
| **선택 수정** | `RabbitMqConfig`, `RabbitMqKeys` v1·fan-out 잔존 |
| **MQ UI** | v1 큐·binding·(선택) exchange 삭제 |
| **수정** | docs/process, docs/db, plan.md |

---

## 검증 체크리스트

- [ ] account-service 컴파일·기동 성공
- [ ] account REST `/api/v1/users` 정상
- [ ] account internal API 정상
- [ ] processing v2 E2E 정상 (Phase 3와 동일)
- [ ] 삭제한 클래스에 대한 import 잔존 없음
- [ ] RabbitMQ에 v1 account 큐·binding 없음 (또는 미사용)
- [ ] 문서와 실제 소유 테이블·MQ 계약 일치

---

## 롤백

Phase 4는 **삭제 작업**이므로 롤백 = git revert 또는 `v2-pre-cleanup` 태그 체크아웃.

v1 Consumer 복구 시 **order v1 발행·v1 MQ binding**도 함께 복구해야 v1 E2E가 동작한다.

---

## 커밋 메시지 예시

```text
chore(account-service): [point] v1 UserPointChanged Consumer 및 PointHistory 제거
```

```text
docs: v2 아키텍처 기준 process·tables 문서 갱신
```

---

## 완료 기준

- [ ] v2만으로 `UserPointChanged` 전 구간 운영 가능
- [ ] v1 dead code 없음
- [ ] 문서·코드·MQ v2 계약 일치

마이그레이션 종료. 이후 기능 변경은 [v2_plan.md](../v2_plan.md)을 기준으로 한다.
