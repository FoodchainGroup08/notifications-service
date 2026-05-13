package com.microservices.notifications_service.service;

import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.entity.NotificationLog;
import com.microservices.notifications_service.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    // ── User notification management ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<NotificationDtos.NotificationHistoryResponse> getHistory(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByUserIdOrderBySentAtDesc(userId, pageable)
                .map(this::toHistoryResponse);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return repository.countByCustomerIdAndIsRead(userId, false);
    }

    @Transactional
    public NotificationDtos.NotificationHistoryResponse markRead(Long id, String userId) {
        int updated = repository.markReadByIdAndUserId(id, userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Notification not found or does not belong to user");
        }
        NotificationLog log = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        return toHistoryResponse(log);
    }

    @Transactional
    public int markAllRead(String userId) {
        return repository.markAllReadByUserId(userId);
    }

    @Transactional
    public void delete(Long id, String userId) {
        NotificationLog entry = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!userId.equals(entry.getCustomerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete another user's notification");
        }
        repository.delete(entry);
    }

    public NotificationDtos.NotificationHistoryResponse toHistoryResponse(NotificationLog n) {
        return NotificationDtos.NotificationHistoryResponse.builder()
                .id(n.getId())
                .userId(n.getCustomerId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getNotificationType() != null ? n.getNotificationType().name() : null)
                .channel(null)
                .relatedEntityType(n.getOrderId() != null ? "ORDER" : null)
                .relatedEntityId(n.getOrderId())
                .status(n.getOrderStatus())
                .isRead(n.getIsRead() != null && n.getIsRead())
                .retryCount(0)
                .sentAt(n.getSentAt() != null ? n.getSentAt().toString() : null)
                .readAt(n.getReadAt() != null ? n.getReadAt().toString() : null)
                .createdAt(n.getSentAt() != null ? n.getSentAt().toString() : null)
                .build();
    }

    private void save(NotificationLog entry) {
        try {
            repository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist notification log: {}", e.getMessage());
        }
    }
}
