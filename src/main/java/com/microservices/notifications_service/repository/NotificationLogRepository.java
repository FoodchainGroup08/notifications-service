package com.microservices.notifications_service.repository;

import com.microservices.notifications_service.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByOrderIdOrderBySentAtDesc(String orderId, Pageable pageable);

    Page<NotificationLog> findByCustomerIdOrderBySentAtDesc(String customerId, Pageable pageable);

    Page<NotificationLog> findAllByOrderBySentAtDesc(Pageable pageable);
}
