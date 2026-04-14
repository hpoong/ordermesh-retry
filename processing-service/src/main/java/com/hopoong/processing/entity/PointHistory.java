package com.hopoong.processing.entity;

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
        name = "point_histories",
        indexes = {
                @Index(name = "idx_point_histories_user_id", columnList = "user_id"),
                @Index(name = "idx_point_histories_order_id", columnList = "order_id"),
                @Index(name = "idx_point_histories_process_status", columnList = "process_status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_point_histories_event_id", columnNames = "event_id")
        }
)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "point_type", nullable = false, length = 30)
    private String pointType;

    @Column(name = "point_amount", nullable = false)
    private Integer pointAmount;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "process_status", nullable = false, length = 30)
    private String processStatus;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
