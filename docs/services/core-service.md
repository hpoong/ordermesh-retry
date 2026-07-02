# core-service 프로세스

## 1. 서비스 역할

`core-service`는 여러 서비스가 함께 사용하는 공통 기능과 계약을 모아둔 모듈입니다.

직접 사용자 요청을 처리하는 비즈니스 서비스라기보다는, 다른 서비스들이 같은 규칙으로 동작하도록 도와주는 기반 역할을 합니다.

주요 역할은 다음과 같습니다.

- 공통 이벤트 구조 정의
- RabbitMQ exchange, queue, routing key 설정
- Redis key 규칙 제공
- 공통 응답 형식 제공
- 공통 예외 처리 방식 제공
- 요청 추적용 request id 처리
- Flyway DB 마이그레이션 관리

## 2. 전체 흐름

```text
account-service
order-service
processing-service
recovery-service
  -> core-service의 공통 코드 사용
     -> 이벤트 계약
     -> MQ 설정
     -> Redis key
     -> 응답/예외 형식
     -> DB migration
```

`core-service`는 특정 도메인의 업무 처리를 직접 수행하지 않습니다. 대신 서비스들이 같은 이벤트 이름, 같은 응답 포맷, 같은 메시지 큐 설정을 사용하도록 기준을 제공합니다.

## 3. 공통 이벤트 계약

현재 핵심 이벤트는 `UserPointChangedEvent`입니다.

이 이벤트는 사용자 포인트가 변경되어야 할 때 서비스 간에 전달됩니다.

| 필드 | 설명 |
| --- | --- |
| `eventId` | 이벤트 고유 ID |
| `eventType` | 이벤트 종류 |
| `eventVersion` | 이벤트 버전 |
| `userId` | 대상 사용자 ID |
| `orderId` | 관련 주문 ID |
| `pointType` | 포인트 유형 |
| `changeAmount` | 변경된 포인트 양 |
| `balanceAfter` | 변경 후 최종 잔액 |
| `occurredAt` | 이벤트 발생 시각 |

흐름은 다음과 같습니다.

```text
1. order-service가 UserPointChangedEvent 생성
2. event_logs에 이벤트 저장
3. RabbitMQ로 이벤트 발행
4. processing-service가 이벤트 수신
5. processing-service가 account-service에 잔액 반영 요청
```

## 4. RabbitMQ 설정 프로세스

`core-service`는 RabbitMQ 설정을 공통으로 제공합니다.

현재 `UserPointChanged` 이벤트에 사용하는 값은 다음과 같습니다.

| 항목 | 값 |
| --- | --- |
| Exchange | `user.events` |
| Routing Key | `user.point.changed` |
| Queue | `processing-service.user.point.changed.v2` |
| DLQ | `processing-service.user.point.changed.v2.dlq` |

처리 흐름은 다음과 같습니다.

```text
1. TopicExchange 생성
2. Queue 생성
3. Exchange와 Queue를 routing key로 연결
4. JSON 메시지 변환기 설정
5. RabbitTemplate과 ListenerContainerFactory에서 동일한 변환기 사용
```

이 설정 덕분에 발행 서비스와 소비 서비스가 같은 메시지 형식을 사용합니다.

## 5. Redis key 규칙

사용자 상세 캐시는 `core-service`의 Redis key 규칙을 사용합니다.

```text
user:detail:v1:{userId}
```

예를 들어 사용자 ID가 `1`이면 다음 key를 사용합니다.

```text
user:detail:v1:1
```

이 key는 `account-service`에서 사용자 상세 정보를 캐싱하거나 삭제할 때 사용합니다.

## 6. 공통 응답 처리

API 응답은 공통 응답 객체를 사용합니다.

성공 응답은 다음 형태를 가집니다.

```json
{
  "success": true,
  "type": "T1",
  "code": "C01",
  "message": "Success",
  "data": {}
}
```

서비스별 응답 코드는 `CommonResponseCodeEnum`에서 관리합니다.

| 코드 | 용도 |
| --- | --- |
| `ACCOUNT_USERS` | 사용자 API |
| `ACCOUNT_PRODUCTS` | 상품 API |
| `ORDER_POINT` | 주문/포인트 API |
| `INVALID_REQUEST` | 잘못된 요청 |
| `SERVER` | 서버 오류 |

## 7. 공통 예외 처리

REST API에서 발생하는 예외는 `GlobalExceptionHandler`가 공통 형식으로 변환합니다.

주요 처리 방식은 다음과 같습니다.

| 상황 | 처리 |
| --- | --- |
| 잘못된 요청값 | 400 응답 |
| 필수 파라미터 누락 | 400 응답 |
| 잘못된 요청 본문 | 400 응답 |
| 지원하지 않는 HTTP Method | 405 응답 |
| 리소스 없음 | 404 응답 |
| 중복 데이터 | 409 응답 |
| 예상하지 못한 오류 | 500 응답 |

각 서비스는 `CoreException`을 사용해 `badRequest`, `notFound`, `conflict` 같은 오류를 명확하게 표현할 수 있습니다.

## 8. Request ID 처리

요청이 들어오면 `RequestIdFilter`가 요청 추적 ID를 관리합니다.

```text
1. 요청 헤더에서 request id 확인
2. 없으면 새 UUID 생성
3. 로그 MDC에 request id 저장
4. 응답 헤더에 request id 추가
5. 요청 처리가 끝나면 MDC에서 제거
```

이 처리는 여러 서비스 로그를 추적할 때 도움이 됩니다.

## 9. DB 마이그레이션 관리

DB 테이블 생성과 변경은 `core-service`의 Flyway migration 파일에서 관리합니다.

주요 테이블은 다음과 같습니다.

| 서비스 | 테이블 |
| --- | --- |
| account-service | `users`, `products` |
| order-service | `orders`, `order_items`, `payments`, `event_logs` |
| processing-service | `point_histories`, `message_process_logs` |
| recovery-service | `failed_messages` |

서비스는 나뉘어 있지만, 현재 스키마 생성 기준은 `core-service/src/main/resources/db/migration` 아래에 모여 있습니다.

## 10. 정리

`core-service`는 실제 주문, 사용자, 포인트 업무를 직접 처리하지 않습니다.

대신 여러 서비스가 같은 규칙으로 통신하고, 같은 방식으로 응답하고, 같은 이벤트 계약을 사용하도록 공통 기반을 제공합니다. 따라서 이 서비스의 핵심은 비즈니스 로직이 아니라 서비스 간 약속을 안정적으로 유지하는 것입니다.
