# order-service 프로세스

## 1. 서비스 역할

`order-service`는 주문과 결제 데이터를 관리하고, 포인트 변경 이벤트를 발행하는 서비스입니다.

현재 코드에서 가장 중요한 흐름은 `UserPointChanged` 이벤트를 Outbox에 저장한 뒤 RabbitMQ로 발행하는 과정입니다.

주요 역할은 다음과 같습니다.

- 주문 기본 정보 관리
- 주문 상품 정보 관리
- 결제 정보 관리
- 포인트 변경 이벤트 Outbox 기록
- Outbox 이벤트 RabbitMQ 발행
- 발행 실패 시 재시도 관리

## 2. 전체 흐름

```text
사용자/테스트 화면
  -> order-service
     -> UserPointChanged 이벤트 생성
     -> event_logs에 READY 상태로 저장

스케줄러
  -> READY/RETRYING 이벤트 조회
  -> RabbitMQ 발행
  -> 성공 시 PUBLISHED
  -> 실패 시 RETRYING 또는 FAILED
```

`order-service`는 포인트 잔액을 직접 변경하지 않습니다. 포인트 변경이 필요하다는 이벤트를 만들고, 이후 처리는 `processing-service`와 `account-service`가 이어서 담당합니다.

## 3. Outbox 기록 프로세스

포인트 변경 이벤트는 다음 API로 기록됩니다.

```text
POST /api/outbox/user-point-changed
```

요청에는 다음 정보가 포함됩니다.

| 필드 | 설명 |
| --- | --- |
| `userId` | 포인트 변경 대상 사용자 ID |
| `orderId` | 관련 주문 ID |
| `pointType` | 포인트 유형 |
| `changeAmount` | 변경된 포인트 양 |
| `balanceAfter` | 변경 후 최종 잔액 |
| `occurredAt` | 이벤트 발생 시각 |

처리 흐름은 다음과 같습니다.

```text
1. 포인트 변경 이벤트 기록 요청
2. eventId 생성
3. UserPointChangedEvent 생성
4. 이벤트 내용을 JSON으로 변환
5. event_logs 테이블에 READY 상태로 저장
6. 저장된 이벤트 로그 응답
```

이때 이벤트에는 다음 값이 저장됩니다.

| 항목 | 값 |
| --- | --- |
| `eventType` | `USER_POINT_CHANGED` |
| `eventVersion` | `v2` |
| `exchangeName` | `user.events` |
| `routingKey` | `user.point.changed` |
| `publishStatus` | `READY` |

## 4. Outbox 발행 프로세스

`EventLogPublishScheduler`가 일정 간격으로 발행할 이벤트를 확인합니다.

기본 설정은 다음과 같습니다.

| 설정 | 값 |
| --- | --- |
| 활성화 여부 | `enabled: true` |
| 한 번에 처리할 최대 개수 | `batch-size: 20` |
| 최대 발행 시도 횟수 | `max-attempts: 10` |
| 실패 후 재시도 대기 시간 | `retry-delay-seconds: 30` |
| 스케줄러 실행 간격 | `fixed-delay-ms: 60000` |

처리 흐름은 다음과 같습니다.

```text
1. 스케줄러 실행
2. event_logs에서 READY 또는 RETRYING 이벤트 조회
3. nextRetryAt이 미래인 이벤트는 제외
4. 발행 대상 이벤트 선점
5. publishAttemptCount 증가
6. RabbitMQ로 메시지 발행
7. 성공하면 PUBLISHED 상태로 변경
8. 실패하면 재시도 또는 최종 실패 처리
```

## 5. RabbitMQ 발행 방식

RabbitMQ 발행은 `UserPointChangedEventPublisher`가 담당합니다.

```text
1. event_logs.payload를 메시지 본문으로 사용
2. eventId를 messageId로 설정
3. contentType을 application/json으로 설정
4. event_logs의 exchangeName과 routingKey로 발행
```

발행된 메시지는 `processing-service.user.point.changed.v2` 큐로 전달되고, `processing-service`가 소비합니다.

## 6. 발행 상태 관리

`event_logs.publish_status`로 이벤트 발행 상태를 관리합니다.

| 상태 | 설명 |
| --- | --- |
| `READY` | 발행 대기 |
| `RETRYING` | 발행 시도 중이거나 재시도 대기 |
| `PUBLISHED` | 발행 성공 |
| `FAILED` | 최대 재시도 횟수 초과로 최종 실패 |

실패하면 실패 사유와 다음 재시도 시각을 저장합니다. 재시도 횟수가 최대값을 넘으면 더 이상 발행하지 않고 `FAILED`로 남깁니다.

## 7. 데이터 저장

`order-service`가 주로 사용하는 테이블은 다음과 같습니다.

| 테이블 | 설명 |
| --- | --- |
| `orders` | 주문 기본 정보 |
| `order_items` | 주문 상품 목록 |
| `payments` | 결제 정보 |
| `event_logs` | RabbitMQ로 발행할 이벤트 Outbox |

현재 포인트 변경 이벤트 발행 흐름에서는 `event_logs`가 핵심 테이블입니다.

## 8. 주요 예외 상황

| 상황 | 처리 |
| --- | --- |
| 이벤트 JSON 변환 실패 | 이벤트 기록 실패 |
| RabbitMQ 발행 실패 | `RETRYING` 상태로 재시도 예약 |
| 최대 재시도 초과 | `FAILED` 상태로 변경 |
| 이미 다른 실행에서 처리한 이벤트 | 다시 선점하지 않고 건너뜀 |

## 9. 정리

`order-service`의 핵심은 포인트 변경 결과를 이벤트로 남기고 안정적으로 발행하는 것입니다.

주문 처리와 포인트 잔액 반영을 한 번에 묶지 않고, Outbox에 먼저 저장한 뒤 스케줄러가 RabbitMQ로 발행합니다. 이 구조 덕분에 RabbitMQ가 잠시 실패해도 이벤트를 잃지 않고 재시도할 수 있습니다.
