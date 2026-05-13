package com.microservices.notifications_service.repository;

import com.microservices.notifications_service.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(String userId);

    List<Notification> findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            String status, int maxRetries);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now, n.status = 'READ', n.updatedAt = :now " +
           "WHERE n.userId = :userId AND n.isRead = false")
    int markAllReadByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);

    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetries " +
           "AND n.updatedAt < :cutoff ORDER BY n.updatedAt ASC")
    List<Notification> findRetryable(@Param("maxRetries") int maxRetries,
                                     @Param("cutoff") LocalDateTime cutoff);
}
