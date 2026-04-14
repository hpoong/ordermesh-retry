package com.hopoong.processing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "message_process_logs",
        indexes = {
                @Index(name = "idx_message_process_logs_status", columnList = "process_status"),
                @Index(name = "idx_message_process_logs_received_at", columnList = "received_at"),
                @Index(name = "idx_message_process_logs_trace_id", columnList = "trace_id"),
                @Index(name = "idx_message_process_logs_event_type", columnList = "event_type")
        }
)
public class MessageProcessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, length = 100)
    private String messageId;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "queue_name", nullable = false, length = 100)
    private String queueName;

    @Column(name = "process_status", nullable = false, length = 30)
    private String processStatus;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "acked_at")
    private LocalDateTime ackedAt;

    @Column(name = "duplicate_yn", nullable = false, length = 1)
    private String duplicateYn;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "trace_id", nullable = false, length = 100)
    private String traceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
