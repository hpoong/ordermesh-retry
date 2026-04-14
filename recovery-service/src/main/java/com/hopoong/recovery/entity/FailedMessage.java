package com.hopoong.recovery.entity;

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
        name = "failed_messages",
        indexes = {
                @Index(name = "idx_failed_messages_reprocess_status", columnList = "reprocess_status"),
                @Index(name = "idx_failed_messages_last_failed_at", columnList = "last_failed_at"),
                @Index(name = "idx_failed_messages_failure_type", columnList = "failure_type")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_failed_messages_consumer_event", columnNames = {"consumer_name", "event_id"})
        }
)
public class FailedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "queue_name", nullable = false, length = 100)
    private String queueName;

    @Column(name = "exchange_name", nullable = false, length = 100)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "failure_type", nullable = false, length = 50)
    private String failureType;

    @Column(name = "failure_reason", nullable = false, columnDefinition = "text")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;

    @Column(name = "dlq_stored_yn", nullable = false, length = 1)
    private String dlqStoredYn;

    @Column(name = "reprocess_status", nullable = false, length = 30)
    private String reprocessStatus;

    @Column(name = "reprocessed_at")
    private LocalDateTime reprocessedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
