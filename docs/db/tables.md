# 테이블 스키마 및 서비스별 엔티티 현황

## 테이블 스키마 참고 기준

컬럼, 제약조건, 인덱스, FK, 기본값 등 모든 테이블의 상세 스키마는 Flyway 마이그레이션 파일을 기준으로 확인하세요.

- 경로: `core-service/src/main/resources/db/migration`

서비스는 논리적으로 분리되어 있지만, 현재 모든 DB 스키마 정의는 위 경로에서 관리합니다.

---

## 서비스별 엔티티 현황

### core-service

* `users`
    * 사용자 정보

* `products`
    * 상품 정보

### order-service

* `orders`
    * 주문 기본 정보

* `order_items`
    * 주문 상품 목록

* `payments`
    * 결제 상태

* `event_logs`
    * 발행한 이벤트 로그 저장

### processing-service

* `point_histories`
    * 포인트 적립 이력

* `message_process_logs`
    * Consumer 처리 성공/실패/중복 여부 기록

### recovery-service

* `failed_messages`
    * 실패 메시지 관리
