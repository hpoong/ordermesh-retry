# 테이블 스키마 및 서비스별 엔티티 현황

## 테이블 스키마 참고 기준

컬럼, 제약조건, 인덱스, FK, 기본값 등 모든 테이블의 상세 스키마는 Flyway 마이그레이션 파일을 기준으로 확인하세요.

- 경로: `core-service/src/main/resources/db/migration`

서비스는 논리적으로 분리되어 있지만, 현재 모든 DB 스키마 정의는 위 경로에서 관리합니다.

---

## 서비스별 엔티티 현황

> JPA `@Entity` 기준. `core-service`는 스키마(Flyway)만 관리하며 엔티티 클래스는 없습니다.

### account-service

| 테이블 | 엔티티 | 용도 |
|--------|--------|------|
| `users` | `UserEntity` | 사용자 정보, `point_balance` |
| `products` | `ProductEntity` | 상품 정보 |

### order-service

| 테이블 | 엔티티 | 용도 |
|--------|--------|------|
| `orders` | `Order` | 주문 기본 정보 |
| `order_items` | `OrderItem` | 주문 상품 목록 |
| `payments` | `Payment` | 결제 상태 |
| `event_logs` | `EventLog` | 발행 이벤트 Outbox 로그 |

### processing-service

| 테이블 | 엔티티 | 용도 |
|--------|--------|------|
| `point_histories` | `PointHistory` | 포인트 적립·차감 이력 (멱등: `event_id`) |
| `message_process_logs` | `MessageProcessLog` | Consumer 처리 성공/실패/중복 기록 |

### recovery-service

| 테이블 | 엔티티 | 용도 |
|--------|--------|------|
| `failed_messages` | `FailedMessage` | 실패 메시지 관리 |

