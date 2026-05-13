package com.microservices.notifications_service.repository;

import com.microservices.notifications_service.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByOrderIdOrderBySentAtDesc(String orderId, Pageable pageable);

    Page<NotificationLog> findByCustomerIdOrderBySentAtDesc(String customerId, Pageable pageable);

    Page<NotificationLog> findAllByOrderBySentAtDesc(Pageable pageable);

    /** Paginated notification history for a specific user, newest first. */
    @Query("SELECT n FROM NotificationLog n WHERE n.customerId = :userId ORDER BY n.sentAt DESC")
    Page<NotificationLog> findByUserIdOrderBySentAtDesc(@Param("userId") String userId, Pageable pageable);

    /** Count unread notifications for a user. */
    long countByCustomerIdAndIsRead(String customerId, boolean isRead);

    /** Mark a single notification as read, scoped to owner. */
    @Modifying
    @Query("UPDATE NotificationLog n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.id = :id AND n.customerId = :userId")
    int markReadByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);

    /** Mark all unread notifications as read for a user. */
    @Modifying
    @Query("UPDATE NotificationLog n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.customerId = :userId AND n.isRead = false")
    int markAllReadByUserId(@Param("userId") String userId);
}
