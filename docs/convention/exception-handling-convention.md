# Exception Handling Convention

서비스별 예외 처리 경계와 사용 규칙입니다.

## Purpose (목적)

- HTTP API와 비동기 처리(Consumer, Scheduler 등)의 실패 모델을 분리한다.
- REST 응답 형식은 `CoreException` + `GlobalExceptionHandler`로 통일한다.
- MQ·배치 등 비REST 경로는 도메인 예외로 의도를 드러내고, ack / retry / DLQ 정책을 별도로 정한다.

## Boundary (경계)

| 경계 | 대상 | 사용 예외 | 처리 주체 |
|------|------|-----------|-----------|
| **REST** | `@RestController` → Service | `CoreException` | `GlobalExceptionHandler` → HTTP `ErrorResponse` |
| **비REST** | `@RabbitListener`, Scheduler, 내부 배치 등 | 도메인 예외 (`RuntimeException` 하위) | Listener ErrorHandler, 로그, retry/DLQ 정책 |

**핵심 원칙:** `CoreException`은 HTTP 응답용이다. Consumer 예외를 `CoreException`으로 통일하지 않는다.

---

## REST → `CoreException`

### 적용 범위

- Controller에서 호출하는 Service·Repository 실패
- 클라이언트에게 HTTP status + `CommonResponseCodeEnum` + 메시지를 반환해야 하는 경우

### 사용 방법

- `core-service`의 `CoreException` 팩토리 메서드만 사용한다.
- `CommonResponseCodeEnum`으로 도메인을 구분한다. (예: `ACCOUNT_USERS`, `ACCOUNT_PRODUCTS`)

| 팩토리 메서드 | HTTP Status | 용도 |
|---------------|-------------|------|
| `badRequest` | 400 | 잘못된 파라미터, 유효하지 않은 입력값 |
| `notFound` | 404 | 단건 조회 실패 |
| `conflict` | 409 | unique 제약 위반, 중복 리소스 |
| `unauthorized` | 401 | 인증 실패 |
| `forbidden` | 403 | 권한 부족 |
| `unprocessable` | 422 | 현재 상태에서 허용되지 않는 요청 |
| `internalError` | 500 | 예상치 못한 서버 오류 |

### REST 예시

```java
// 사용자 미존재
.orElseThrow(() -> CoreException.notFound(ACCOUNT_USERS, "사용자를 찾을 수 없습니다."));

// loginId 중복
throw CoreException.conflict(ACCOUNT_USERS, "이미 사용중인 loginId 입니다.");
```

### REST에서 하지 않는 것

- `throw new RuntimeException(...)` 으로 API 오류를 던지지 않는다.
- Consumer 전용 도메인 예외를 REST Service에서 던지지 않는다.
- `GlobalExceptionHandler`가 처리하지 않는 예외를 REST 경로에 남겨두지 않는다. (최종적으로 500 generic 응답이 됨)

### 참고 클래스

- `core-service/.../exception/CoreException.java`
- `core-service/.../exception/GlobalExceptionHandler.java`
- `account-service/.../api/user/service/UserService.java`
- `account-service/.../api/product/service/ProductService.java`

---

## 비REST → 도메인 예외

### 적용 범위

- `@RabbitListener` Consumer
- `@Scheduled` Scheduler
- HTTP 요청 컨텍스트 밖의 비동기·배치 처리

### 사용 방법

- 해당 기능 패키지 하위 `exception/` 에 도메인 예외를 둔다.
- 예외 클래스명은 **무엇이 실패했는지** 드러나게 짓는다. (예: `UserPointChangedProcessException`)
- `HttpStatus`, `CommonResponseCodeEnum`을 붙이지 않는다.
- 멱등·중복처럼 **정상 종료(ack)** 가 맞는 경우는 예외를 던지지 않고 로그 후 return 한다.

### 비REST 예시 (`UserPointChanged`)

| 상황 | 처리 |
|------|------|
| 중복 `eventId` | warn 로그, return (ack) — 예외 throw 금지 |
| payload 필수값 누락 | `UserPointChangedProcessException` |
| 사용자 미존재 | `UserPointChangedProcessException` |
| 미지원 `eventVersion` | `UnsupportedUserPointChangedVersionException` |
| 잘못된 `eventType` | `IllegalArgumentException` 또는 도메인 예외 |
| 동시 중복 INSERT | `DataIntegrityViolationException` catch → warn, return (ack) |

### 비REST에서 하지 않는 것

- `CoreException`을 Consumer·Scheduler에서 던지지 않는다. (`HttpStatus`가 MQ 맥락과 맞지 않음)
- `GlobalExceptionHandler`가 비REST 예외를 처리할 것이라 기대하지 않는다.
- 비REST 실패를 REST 응답 형식(`ErrorResponse`)으로 변환하려 하지 않는다.

### 참고 클래스

- `account-service/.../consumer/user/point/exception/UserPointChangedProcessException.java`
- `account-service/.../consumer/user/point/exception/UnsupportedUserPointChangedVersionException.java`
- `account-service/.../consumer/user/point/service/UserPointChangedProcessService.java`

구현 기준: [`../plan/plan.md`](../plan/plan.md) 섹션 4.3 (예외 정책)

---

## 비REST 후속 처리 (ack / retry / DLQ)

도메인 예외를 정의한 뒤, **Listener ErrorHandler**에서 타입별 정책을 명시한다.

| 예외 유형 | 권장 방향 (예시) |
|-----------|------------------|
| 멱등·중복 | 예외 없이 ack |
| 일시적 오류 (DB 연결 등) | retry |
| 복구 불가 (미지원 버전, 영구적 데이터 오류) | DLQ 또는 skip + 운영 알림 |

정책이 확정되기 전까지는 도메인 예외를 유지하고, `RabbitListenerErrorHandler` 추가 시 이 문서와 plan을 함께 갱신한다.

---

## 새 기능 추가 시 체크리스트

### REST API를 추가할 때

- [ ] 실패 시 `CoreException` + 적절한 `CommonResponseCodeEnum`을 사용했는가?
- [ ] `CommonResponseCodeEnum`에 해당 도메인 코드가 없으면 enum을 먼저 추가했는가?
- [ ] `GlobalExceptionHandler`가 처리 가능한 예외만 Service에서 던지는가?

### Consumer / Scheduler를 추가할 때

- [ ] `exception/` 패키지에 도메인 예외를 두었는가?
- [ ] 멱등·중복은 예외 throw 없이 정상 종료하는가?
- [ ] `CoreException`을 사용하지 않았는가?
- [ ] (선택) Listener ErrorHandler에 retry/DLQ 정책을 매핑했는가?

### 리뷰 시 공통

- [ ] REST와 비REST 경계를 넘어 예외 타입을 섞지 않았는가?
- [ ] 한 커밋에 REST 예외 정책 변경과 Consumer 정책 변경이 불필요하게 섞이지 않았는가?

