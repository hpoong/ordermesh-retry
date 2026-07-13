# UserPointChanged 전체 흐름

> 포인트 변경 이벤트(`USER_POINT_CHANGED` v2) 기준

---

## [order-service]

- 포인트 변경 요청 수신 (`POST /api/outbox/user-point-changed`)
- `UserPointChangedEvent` 생성 (`balanceAfter` 포함)
- `event_logs`에 `READY` 상태로 저장 (Outbox)
- 스케줄러(`EventLogPublishScheduler`)가 `READY` / `RETRYING` 이벤트 조회
- RabbitMQ 발행 (`user.events` → `user.point.changed`)
    - 성공 → `PUBLISHED`
    - **발행 실패 → order-service가 Outbox 재시도** (`RETRYING` + `nextRetryAt` 예약, 최대 10회 / 30초 간격)
    - 재시도 초과 → `FAILED` (더 이상 발행하지 않음)

> order-service는 포인트 잔액을 직접 변경하지 않음  
> **MQ 발행 재시도는 order-service 책임** — processing·recovery와 별개

---

## [RabbitMQ]

- main 큐: `processing-service.user.point.changed.v2`
    - processing-service가 단독 소비
- ingest 큐: `recovery-service.failed-messages.ingest`
    - routing key: `failed-messages.ingest` (`user.events` exchange)
    - processing-service `FailedMessagePublisher`가 발행, recovery-service가 소비
- DLQ: `processing-service.user.point.changed.v2.dlq`
    - main 큐 DLX 백업, recovery-service가 동일 ingest 서비스로 적재

---

## [processing-service]

- 포인트 MQ 구독 (`UserPointChangedConsumer`)
- 이벤트 타입·버전 확인 (`USER_POINT_CHANGED` / `v2`)
- `message_process_logs` `RECEIVED` 기록 (INSERT)
- `eventId` 기준 중복 검사
    - 중복 → `message_process_logs` `DUPLICATE` 기록 후 종료 (account 미호출)
- `message_process_logs` `PROCESSING` 기록 (UPDATE)
- account-service Internal API 호출 (`POST /internal/v1/users/point-changed`)
    - 성공 → 다음 단계 진행
    - **호출 실패 분기**
        - `4xx`(복구 불가) → `message_process_logs` `FAILED` 기록 후 `FailedMessagePublisher`로 ingest 큐 전달 (main ack)
        - `5xx`/타임아웃(일시 오류) → `RETRY` 기록 + 예외 재전파, retry 소진 시 `FAILED` + ingest 큐 전달
- `point_histories`에 포인트 처리 이력 저장 (INSERT)
    - **중복 `eventId` → `message_process_logs` `DUPLICATE` 기록 후 종료**
- `message_process_logs` `SUCCESS` 기록 (UPDATE)

> processing-service는 `users` 잔액을 직접 수정하지 않음

---

## [account-service]

- processing-service의 Internal API 요청 수신
- 사용자 조회 + row 잠금
- `pointBalance`를 `balanceAfter`로 설정 (증감 계산 없음)
- Redis 사용자 상세 캐시 삭제
- 성공 응답

> account-service는 MQ를 직접 소비하지 않음

---

## [recovery-service]

- ingest 큐·DLQ 구독 → `failed_messages` 적재
- `(consumer_name, event_id)` UK 기준 멱등 upsert
- 실패 메시지 조회 Internal API (`GET /internal/v1/failed-messages`)
- 재처리 API — 미구현

> recovery-service는 실패 메시지 보관만 담당

---

## 한 줄 요약

```
order → RabbitMQ → processing → account
processing 실패 → ingest 큐 → recovery (failed_messages)
```

