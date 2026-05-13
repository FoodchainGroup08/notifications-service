package com.microservices.notifications_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_order_id",    columnList = "order_id"),
        @Index(name = "idx_customer_id", columnList = "customer_id"),
        @Index(name = "idx_sent_at",     columnList = "sent_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog {

    public enum NotificationType {
        ORDER_RECEIVED, STATUS_UPDATE, ORDER_READY, EMAIL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "branch_id", length = 64)
    private String branchId;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "order_status", length = 50)
    private String orderStatus;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
