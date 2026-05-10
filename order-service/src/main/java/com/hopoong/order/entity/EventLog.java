package com.hopoong.order.entity;

import com.hopoong.order.enums.EventPublishStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "event_logs",
        indexes = {
                @Index(name = "idx_event_logs_event_type", columnList = "event_type"),
                @Index(name = "idx_event_logs_publish_status", columnList = "publish_status"),
                @Index(name = "idx_event_logs_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_event_logs_publish_status_occurred_at", columnList = "publish_status, occurred_at"),
                @Index(
                        name = "idx_event_logs_publish_status_next_retry_at_occurred_at",
                        columnList = "publish_status, next_retry_at, occurred_at"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_event_logs_event_id", columnNames = "event_id")
        }
)
public class EventLog {

    @Comment("이벤트 로그 PK")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("이벤트 고유 식별자, 중복 발행 방지를 위해 유니크하게 관리")
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Comment("이벤트 타입, 예: ORDER_CREATED, PAYMENT_COMPLETED")
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Comment("이벤트 스키마 버전")
    @Column(name = "event_version", nullable = false, length = 20)
    private String eventVersion;

    @Comment("RabbitMQ 발행 시 사용하는 라우팅 키")
    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Comment("RabbitMQ 발행 대상 exchange 이름")
    @Column(name = "exchange_name", nullable = false, length = 100)
    private String exchangeName;

    @Comment("발행할 이벤트 본문 데이터")
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Comment("발행 상태값, 예: READY, PUBLISHED, FAILED, RETRYING")
    @Column(name = "publish_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private EventPublishStatus publishStatus;

    @Comment("이벤트 발행 시도 횟수")
    @Column(name = "publish_attempt_count", nullable = false)
    private Integer publishAttemptCount;

    @Comment("다음 재발행 예정 시각")
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Comment("마지막 발행 시도 시각")
    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Comment("마지막으로 발행 성공 처리한 시각")
    @Column(name = "last_published_at")
    private LocalDateTime lastPublishedAt;

    @Comment("최초 발행 성공 시각")
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Comment("발행 실패 사유")
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Comment("도메인 이벤트 실제 발생 시각")
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Comment("이벤트 로그 생성 일시")
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Comment("이벤트 로그 최종 수정 일시")
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
