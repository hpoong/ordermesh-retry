# RabbitMQ Key Convention

RabbitMQ 키(exchange, routing key, queue, dlq) 네이밍 규칙입니다.

## Purpose (목적)
- Prevent confusion when naming `exchange`, `routing key`, `queue`, and `dlq`.
  - `exchange`, `routing key`, `queue`, `dlq` 이름을 정할 때 혼동을 줄입니다.
- Keep producer/consumer contracts simple and consistent.
  - producer/consumer 간 계약을 단순하고 일관되게 유지합니다.

## Naming Rules (Minimal) (네이밍 규칙)
1. `exchange`: `{domain}.{entity}.events`
2. `routing key`: `{domain}.{entity}.{event}`
3. `queue`: `{consumer}.{domain}-{entity}-{event}`
4. `dlq`: `{queue}.dlq`
5. Add version suffix only when needed: `.v1`
   - 버전 접미사는 필요할 때만 붙입니다.

## Practical Rule (실무 규칙)
- Keep `routing key` focused on event meaning.
  - `routing key`는 이벤트 의미에 맞춥니다.
- Keep `queue` focused on consumer ownership.
  - `queue`는 소비자(consumer) 소유권에 맞춥니다.
- Do not make `routing key` and `queue` the same by default.
  - 기본값으로 `routing key`와 `queue`를 같게 두지 않습니다.

## Routing Key Policy (routing key 정책)
- Not always required by protocol (for `fanout` exchanges it has no effect).
  - 프로토콜상 항상 필수는 아닙니다(`fanout` exchange에서는 효과가 없음).
- Recommended as a default for real services because future event branching becomes easier.
  - 실서비스에서는 기본으로 쓰는 것을 권장합니다. 이후 이벤트 분기가 쉬워집니다.
- Standard choice: use `direct` or `topic` exchange with explicit `routing key`.
  - 표준 선택: 명시적 `routing key`와 함께 `direct` 또는 `topic` exchange를 사용합니다.

## Example (User Point Changed) (예시: 사용자 포인트 변경)
- `exchange`: `user.point.events`
- `routing key`: `user.point.changed`
- `queue`: `core.user-point-changed`
- `dlq`: `core.user-point-changed.dlq`

## Optional Extension (선택 확장)
- If event types grow, split by event:
  - 이벤트 종류가 늘면 이벤트별로 나눕니다.
  - `user.point.increased`
  - `user.point.decreased`
  - `user.point.adjusted`
