package com.microservices.notifications_service.repository;

import com.microservices.notifications_service.model.NotificationDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationDeliveryAttemptRepository extends JpaRepository<NotificationDeliveryAttempt, Long> {

    List<NotificationDeliveryAttempt> findByNotificationIdOrderByAttemptedAtDesc(Long notificationId);

    long countByNotificationId(Long notificationId);
}
