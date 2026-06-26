# processing-service 프로세스

## 1. 서비스 역할

`processing-service`는 RabbitMQ에서 포인트 변경 이벤트를 소비하고, 필요한 후속 처리를 오케스트레이션하는 서비스입니다.

주요 역할은 다음과 같습니다.

- `UserPointChanged` 이벤트 수신
- 이벤트 타입과 버전 확인
- 중복 이벤트 검사
- account-service에 포인트 잔액 반영 요청
- 포인트 처리 이력 저장
- 메시지 처리 상태 기록

`processing-service`는 사용자 포인트 잔액을 직접 DB에 수정하지 않습니다. 잔액 변경은 `account-service`의 Internal API를 호출해서 처리합니다.

## 2. 전체 흐름

```text
RabbitMQ
  -> processing-service
     -> 이벤트 수신
     -> 이벤트 타입/버전 확인
     -> 처리 로그 RECEIVED 저장
     -> 중복 여부 확인
     -> account-service에 잔액 반영 요청
     -> point_histories 저장
     -> 처리 로그 SUCCESS 저장
```

중복 이벤트라면 account-service를 다시 호출하지 않고 중복 처리 로그만 남긴 뒤 종료합니다.

## 3. 메시지 수신 프로세스

`UserPointChangedConsumer`가 RabbitMQ 큐에서 메시지를 수신합니다.

사용하는 큐는 다음과 같습니다.

```text
processing-service.user.point.changed.v2
```

처리 흐름은 다음과 같습니다.

```text
1. RabbitMQ에서 UserPointChangedEvent 수신
2. eventId, eventVersion, userId 로그 기록
3. Dispatcher에 이벤트 전달
```

Consumer는 메시지를 받는 역할만 담당합니다. 실제 처리 판단은 Dispatcher와 ProcessService가 이어서 담당합니다.

## 4. 이벤트 분기 프로세스

`UserPointChangedEventDispatcher`는 이벤트가 처리 가능한지 확인합니다.

```text
1. eventType 확인
2. USER_POINT_CHANGED가 아니면 실패
3. eventVersion 확인
4. v2 Handler 선택
5. Handler에 이벤트 전달
```

현재 지원하는 버전은 `v2`입니다.

지원하지 않는 버전의 이벤트가 오면 처리하지 않고 오류를 발생시킵니다.

## 5. 포인트 변경 처리 프로세스

실제 처리는 `UserPointChangedProcessService`에서 진행합니다.

```text
1. 필수값 검증
2. pointType 검증
3. message_process_logs에 RECEIVED 기록
4. eventId 기준 중복 검사
5. 중복이면 DUPLICATE 기록 후 종료
6. 처리 상태를 PROCESSING으로 변경
7. account-service Internal API 호출
8. point_histories에 포인트 이력 저장
9. message_process_logs를 SUCCESS로 변경
```

필수값은 다음과 같습니다.

| 필드 | 설명 |
| --- | --- |
| `userId` | 포인트 대상 사용자 |
| `orderId` | 관련 주문 |
| `pointType` | 포인트 유형 |
| `changeAmount` | 변경된 포인트 양 |
| `balanceAfter` | 변경 후 최종 잔액 |

`pointType`은 `EARN`, `CANCEL`, `EXPIRE` 중 하나여야 합니다.

## 6. account-service 호출

`processing-service`는 포인트 잔액을 직접 수정하지 않고 account-service를 호출합니다.

```text
POST {app.account.base-url}/internal/v1/users/point-changed
```

로컬 기본값은 다음과 같습니다.

```text
http://localhost:9000/internal/v1/users/point-changed
```

호출 흐름은 다음과 같습니다.

```text
1. UserPointChangedEvent를 account 요청 형식으로 변환
2. account-service Internal API 호출
3. 2xx 응답이면 다음 단계 진행
4. 4xx 또는 5xx 응답이면 실패 처리
```

account-service는 전달받은 `balanceAfter`를 사용자 최종 잔액으로 저장합니다.

## 7. 중복 처리 방식

이벤트는 RabbitMQ 특성상 같은 메시지가 다시 들어올 수 있습니다.

그래서 `processing-service`는 `eventId`를 기준으로 중복 여부를 확인합니다.

```text
1. point_histories에 같은 eventId가 있는지 확인
2. message_process_logs에 같은 eventId가 성공 또는 중복으로 처리됐는지 확인
3. point_histories 저장 중 unique 제약 충돌이 나도 중복으로 처리
```

중복으로 판단되면 account-service를 다시 호출하지 않습니다.

```text
중복 이벤트
  -> message_process_logs에 DUPLICATE 기록
  -> 정상 종료
```

## 8. 처리 상태 기록

`message_process_logs`는 메시지 처리 과정을 기록합니다.

| 상태 | 설명 |
| --- | --- |
| `RECEIVED` | 메시지를 수신함 |
| `PROCESSING` | 처리 중 |
| `SUCCESS` | 정상 처리 완료 |
| `FAILED` | 처리 실패 |
| `DUPLICATE` | 중복 이벤트로 판단 |
| `RETRY` | 재시도 |
| `DLQ` | DLQ 이동 |

이 로그는 어떤 이벤트가 언제 수신됐고, 성공했는지 실패했는지 확인하는 용도로 사용합니다.

## 9. 포인트 이력 저장

account-service의 잔액 반영이 성공하면 `point_histories`에 포인트 처리 이력을 저장합니다.

저장되는 주요 값은 다음과 같습니다.

| 필드 | 설명 |
| --- | --- |
| `userId` | 사용자 ID |
| `orderId` | 주문 ID |
| `pointType` | 포인트 유형 |
| `pointAmount` | 변경 포인트 |
| `balanceAfter` | 변경 후 잔액 |
| `processStatus` | 처리 상태 |
| `eventId` | 이벤트 ID |
| `processedAt` | 처리 완료 시각 |

`eventId`는 유니크하게 관리되어 같은 이벤트가 두 번 이력으로 저장되지 않도록 막습니다.

## 10. 실패 처리

처리 중 오류가 발생하면 `message_process_logs`에 실패 상태를 남깁니다.

| 상황 | 처리 |
| --- | --- |
| 필수값 누락 | 실패 |
| 잘못된 `pointType` | 실패 |
| 지원하지 않는 이벤트 타입 | 실패 |
| 지원하지 않는 이벤트 버전 | 실패 |
| account-service 4xx 응답 | 실패 |
| account-service 5xx 응답 | 실패 후 재시도 대상 |
| account-service 호출 실패 | 실패 후 재시도 대상 |
| `point_histories` 중복 저장 | 중복으로 처리 후 종료 |

account-service 반영은 성공했지만 이력 저장이 실패한 경우에도 재시도될 수 있습니다. 이때 account-service는 `balanceAfter`를 다시 같은 값으로 설정하므로 잔액이 누적해서 잘못 증가하지 않습니다.

## 11. 데이터 저장

`processing-service`가 사용하는 주요 테이블은 다음과 같습니다.

| 테이블 | 설명 |
| --- | --- |
| `message_process_logs` | 메시지 수신, 처리, 실패, 중복 기록 |
| `point_histories` | 포인트 적립/차감 이력 |

## 12. 정리

`processing-service`는 포인트 이벤트 처리의 중심 오케스트레이터입니다.

이벤트를 받아 중복 여부를 확인하고, account-service에 잔액 반영을 요청한 뒤 처리 이력을 저장합니다. 핵심은 같은 이벤트가 여러 번 들어와도 한 번만 처리되도록 관리하고, 실패 시 원인을 추적할 수 있게 로그를 남기는 것입니다.
