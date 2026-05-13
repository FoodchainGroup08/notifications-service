package com.microservices.notifications_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_user_id",    columnList = "user_id"),
        @Index(name = "idx_notif_status",     columnList = "status"),
        @Index(name = "idx_notif_user_read",  columnList = "user_id, is_read"),
        @Index(name = "idx_notif_created_at", columnList = "created_at"),
        @Index(name = "idx_notif_entity",     columnList = "related_entity_type, related_entity_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user this notification belongs to (customerId, branchManagerId, etc.). */
    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private String userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Logical type of notification:
     * ORDER_RECEIVED | ORDER_PREPARING | ORDER_READY | ORDER_COMPLETED |
     * ORDER_CANCELLED | REPORT_GENERATED | SCHEDULED_REPORT
     */
    @Column(nullable = false, length = 50)
    private String type;

    /**
     * Delivery channel: IN_APP | EMAIL | SMS
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String channel = "IN_APP";

    /** e.g. ORDER, REPORT */
    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    /** UUID of the order or report this notification relates to. */
    @Column(name = "related_entity_id", length = 100)
    private String relatedEntityId;

    /**
     * Delivery status: PENDING | SENT | FAILED | READ
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
