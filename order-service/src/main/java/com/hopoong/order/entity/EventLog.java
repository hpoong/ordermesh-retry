package com.hopoong.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false, length = 20)
    private String eventVersion;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "exchange_name", nullable = false, length = 100)
    private String exchangeName;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "publish_status", nullable = false, length = 30)
    private String publishStatus;

    @Column(name = "publish_attempt_count", nullable = false)
    private Integer publishAttemptCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "last_published_at")
    private LocalDateTime lastPublishedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
