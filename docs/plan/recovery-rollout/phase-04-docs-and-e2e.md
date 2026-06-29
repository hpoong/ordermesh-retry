# Phase 4: 문서·E2E 검증·체크리스트 완료

> **선행:** [Phase 1](phase-01-recovery-bootstrap-and-ingest-api.md) ~ [Phase 3](phase-03-reprocess.md) 완료  
> **기준:** [v2_plan.md](../v2_plan.md) 섹션 8, 13 (체크리스트 11번)

---

## 목적

recovery-service 프로세스 문서를 작성하고, 기존 docs를 실제 구현과 동기화한다.

전 Phase E2E 시나리오를 통합 검증하고, v2_plan 체크리스트 11번을 완료 처리한다.

---

## 전제조건

- [ ] Phase 1~3 기능 구현·단위 검증 완료
- [ ] 로컬 docker-compose 전체 서비스 기동 가능
- [ ] (권장) `git tag recovery-v1` 생성

---

## 작업 목록

### 1. `docs/process/recovery-service.md` 신규 작성

포함 내용:

| 섹션 | 내용 |
|------|------|
| 서비스 역할 | failed_messages 소유, DLQ 연계, 재처리 |
| 전체 흐름 | processing 실패 → API/DLQ → reprocess |
| Internal API | 적재·조회·reprocess 엔드포인트 |
| DLQ Consumer | DLQ → failed_messages 동기화 |
| 재처리 | 수동 API, (선택) Scheduler |
| 데이터 테이블 | `failed_messages` 필드 설명 |
| 실패 유형 | `FailureType`, `ReprocessStatus` |

`docs/process/processing-service.md`, `docs/process/order-service.md` 톤·구조를 따른다.

### 2. `docs/process/processing-service.md` 갱신

**섹션 10. 실패 처리** 표를 Phase 2 구현과 일치시킨다:

| 상황 | 갱신 내용 |
|------|-----------|
| account 4xx | `FAILED` + recovery API + ack |
| account 5xx | `RETRY` → DLQ → recovery |
| 미지원 eventVersion | recovery API + ack |
| retry/DLQ 상태 | enum 실제 사용 반영 |

recovery-service 연동 흐름 문단 추가.

### 3. `docs/process/core-service.md` 갱신

- DLQ Queue Bean·dead-letter binding 설명 추가
- recovery-service 서비스 목록·포트(9300) 반영

### 4. `docs/db/tables.md` 확인

- `failed_messages` 소유 서비스: recovery-service (기존과 일치 확인)
- `failure_type`, `reprocess_status` 값 예시 갱신

### 5. `docs/plan/v2_plan.md` 체크리스트

섹션 12 체크리스트 11번 완료:

```text
11. [x] recovery-service: failed_messages 연동
```

### 6. `docs/plan/v2-migration/README.md` 갱신

recovery-service 섹션을 "범위 외"에서 recovery-init 링크로 변경:

```markdown
## recovery-service

[recovery-init README](recovery-init/README.md) — v2 마이그레이션 완료 후 착수
```

---

## 통합 E2E 검증 시나리오

### 시나리오 A — 정상 (회귀)

1. [ ] Outbox → RabbitMQ → processing SUCCESS
2. [ ] recovery `failed_messages` 행 없음
3. [ ] order `event_logs` Outbox 재시도 정상

### 시나리오 B — BUSINESS 실패

1. [ ] 없는 userId 이벤트 발행
2. [ ] `failed_messages`: `failure_type=BUSINESS`, `reprocess_status=WAITING`
3. [ ] main queue requeue 없음
4. [ ] user 생성 후 reprocess → processing SUCCESS

### 시나리오 C — SYSTEM 실패 → DLQ

1. [ ] account 5xx 유도
2. [ ] `message_process_logs`: `RETRY`, retry_count 증가
3. [ ] DLQ 적재 → recovery Consumer → `dlq_stored_yn=Y`

### 시나리오 D — 멱등

1. [ ] 동일 `(consumer_name, event_id)` API + DLQ 이중 적재 → 단일 행
2. [ ] reprocess 후 processing DUPLICATE (이미 SUCCESS인 경우)

### 시나리오 E — 미지원 버전

1. [ ] 미지원 eventVersion → `failed_messages` BUSINESS 적재 + ack

---

## 성공 기준 (최종 체크리스트)

- [ ] account 404 이벤트가 무한 requeue 없이 `failed_messages`에 BUSINESS로 적재된다
- [ ] 미지원 eventVersion이 `failed_messages`에 적재되고 ack된다
- [ ] 5xx/네트워크 오류가 max_retry_count 후 DLQ → recovery에 적재된다
- [ ] reprocess API로 재발행 시 processing이 정상 처리한다
- [ ] `(consumer_name, event_id)` UK로 중복 적재가 방지된다
- [ ] order-service Outbox 재시도 흐름은 기존과 동일하게 동작한다 (회귀 없음)
- [ ] `docs/process/recovery-service.md` 작성 완료
- [ ] v2_plan 체크리스트 11번 완료

---

## 파일 변경 요약

| 구분 | 범위 |
|------|------|
| **신규** | `docs/process/recovery-service.md` |
| **수정** | `docs/process/processing-service.md`, `docs/process/core-service.md` |
| **수정** | `docs/plan/v2_plan.md`, `docs/plan/v2-migration/README.md` |
| **확인** | `docs/db/tables.md` |
| **코드 변경 없음** | — |

---

## 롤백

| 조치 | 방법 |
|------|------|
| 문서 revert | git revert — 런타임 영향 없음 |
| 체크리스트 | v2_plan 11번 `[ ]` 복원 |

---

## 커밋 메시지 예시

```text
docs: recovery-service 프로세스 문서 및 실패 처리 docs 동기화
```

---

## 완료

recovery-init 4 Phase 구현·검증·문서화 완료.

운영 알림(Slack/이메일), Admin UI, 추가 Consumer 확장은 **후속 스프린트**로 분리한다.
