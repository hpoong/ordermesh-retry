## 서비스별 엔티티 현황

- core-service: `users`, `products`
- order-service: `orders`, `order_items`, `payments`, `event_logs`
- processing-service: `point_histories`, `message_process_logs`
- recovery-service: `failed_messages`

---

## 1. users
- 사용자 정보

## 2. products
- 상품 정보

## 3. orders
- 주문 기본 정보

## 4. order_items
- 주문 상품 목록

## 5. payments
- 결제 상태

## 6. point_histories
- 포인트 적립 이력

## 7. event_logs
- 발행한 이벤트 로그 저장

## 8. failed_messages
- 실패 메시지 관리

## 9. message_process_logs
- Consumer 처리 성공/실패/중복 여부 기록