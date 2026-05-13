package com.microservices.notifications_service.service;

import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.entity.NotificationLog;
import com.microservices.notifications_service.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationLogService {

    private final NotificationLogRepository repository;

    public void logOrderReceived(NotificationDtos.OrderReceivedEvent event,
                                 NotificationDtos.CustomerNotification notification) {
        save(NotificationLog.builder()
                .notificationType(NotificationLog.NotificationType.ORDER_RECEIVED)
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .branchId(event.getBranchId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .orderStatus(event.getStatus())
                .sentAt(LocalDateTime.now())
                .build());
    }

    public void logStatusUpdate(NotificationDtos.OrderStatusUpdatedEvent event,
                                NotificationDtos.CustomerNotification notification) {
        save(NotificationLog.builder()
                .notificationType(NotificationLog.NotificationType.STATUS_UPDATE)
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .branchId(event.getBranchId())
                .recipientEmail(event.getCustomerEmail())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .orderStatus(notification.getStatus())
                .sentAt(LocalDateTime.now())
                .build());
    }

    public void logOrderReady(NotificationDtos.OrderReadyEvent event,
                              NotificationDtos.CustomerNotification notification) {
        save(NotificationLog.builder()
                .notificationType(NotificationLog.NotificationType.ORDER_READY)
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .branchId(event.getBranchId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .orderStatus("READY")
                .sentAt(LocalDateTime.now())
                .build());
    }

    public void logEmail(NotificationDtos.EmailSendEvent event) {
        save(NotificationLog.builder()
                .notificationType(NotificationLog.NotificationType.EMAIL)
                .recipientEmail(event.getToEmail())
                .title(event.getSubject())
                .message(event.getEmailType())
                .sentAt(LocalDateTime.now())
                .build());
    }

    public Page<NotificationLog> findAll(Pageable pageable) {
        return repository.findAllByOrderBySentAtDesc(pageable);
    }

    public Page<NotificationLog> findByOrderId(String orderId, Pageable pageable) {
        return repository.findByOrderIdOrderBySentAtDesc(orderId, pageable);
    }

    public Page<NotificationLog> findByCustomerId(String customerId, Pageable pageable) {
        return repository.findByCustomerIdOrderBySentAtDesc(customerId, pageable);
    }

    private void save(NotificationLog log) {
        try {
            repository.save(log);
        } catch (Exception e) {
            // Log persistence failure must never break the notification delivery
            log.error("Failed to persist notification log: {}", e.getMessage());
        }
    }
}
