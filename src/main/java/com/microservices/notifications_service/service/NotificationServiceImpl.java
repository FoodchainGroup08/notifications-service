package com.microservices.notifications_service.service;

import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.model.Notification;
import com.microservices.notifications_service.model.NotificationDeliveryAttempt;
import com.microservices.notifications_service.repository.NotificationDeliveryAttemptRepository;
import com.microservices.notifications_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_RETRIES = 5;

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryAttemptRepository deliveryAttemptRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    // ── Core operations ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public Notification persist(String userId, String title, String message,
                                String type, String channel,
                                String relatedEntityType, String relatedEntityId) {
        Notification n = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .channel(channel != null ? channel : "IN_APP")
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .status("PENDING")
                .isRead(false)
                .retryCount(0)
                .build();

        Notification saved = notificationRepository.save(n);
        log.debug("Persisted notification id={} type={} userId={}", saved.getId(), type, userId);

        // Attempt immediate delivery
        attemptDelivery(saved);

        return saved;
    }

    @Override
    @Transactional
    public NotificationDtos.NotificationResponse markRead(Long notificationId, String userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found: " + notificationId));

        if (!n.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        if (!Boolean.TRUE.equals(n.getIsRead())) {
            n.setIsRead(true);
            n.setReadAt(LocalDateTime.now());
            n.setStatus("READ");
            notificationRepository.save(n);
        }
        return toResponse(n);
    }

    @Override
    @Transactional
    public int markAllRead(String userId) {
        int updated = notificationRepository.markAllReadByUserId(userId, LocalDateTime.now());
        log.debug("Marked {} notifications as read for userId={}", updated, userId);
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long notificationId, String userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found: " + notificationId));

        if (!n.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        notificationRepository.delete(n);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDtos.NotificationResponse> getHistory(String userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // ── Retry scheduler — runs every 2 minutes ────────────────────────────────

    @Scheduled(fixedDelay = 120_000)
    @Override
    @Transactional
    public void retryFailed() {
        // Exponential backoff cutoff: only retry if last attempt was at least 2^retryCount minutes ago
        LocalDateTime now = LocalDateTime.now();
        List<Notification> candidates = notificationRepository.findRetryable(MAX_RETRIES,
                now.minusMinutes(1)); // broad fetch; backoff enforced per record below

        for (Notification n : candidates) {
            long backoffMinutes = (long) Math.pow(2, n.getRetryCount()); // 1,2,4,8,16 min
            if (n.getUpdatedAt().plusMinutes(backoffMinutes).isAfter(now)) {
                continue; // not yet due
            }
            log.info("Retrying notification id={} attempt={}", n.getId(), n.getRetryCount() + 1);
            attemptDelivery(n);
        }
    }

    // ── Delivery logic ────────────────────────────────────────────────────────

    /**
     * Attempt to push the notification via STOMP to the user's topic.
     * Records the attempt in notification_delivery_attempts.
     * On success → status=SENT, sentAt set.
     * On failure → retryCount++, lastError set, status=FAILED (or PENDING for first attempt).
     */
    private void attemptDelivery(Notification n) {
        LocalDateTime now = LocalDateTime.now();
        boolean success = false;
        String error = null;

        try {
            if ("IN_APP".equals(n.getChannel())) {
                NotificationDtos.CustomerNotification push = NotificationDtos.CustomerNotification.builder()
                        .type(n.getType())
                        .orderId(n.getRelatedEntityId())
                        .title(n.getTitle())
                        .message(n.getMessage())
                        .status(n.getStatus())
                        .timestamp(now)
                        .build();
                messagingTemplate.convertAndSend("/topic/customer/" + n.getUserId(), push);
                log.debug("Pushed notification id={} to /topic/customer/{}", n.getId(), n.getUserId());
            }
            success = true;
        } catch (Exception ex) {
            error = ex.getMessage();
            log.warn("Delivery failed for notification id={}: {}", n.getId(), ex.getMessage());
        }

        // Record attempt
        deliveryAttemptRepository.save(NotificationDeliveryAttempt.builder()
                .notificationId(n.getId())
                .attemptedAt(now)
                .success(success)
                .errorMessage(error)
                .build());

        // Update notification state
        if (success) {
            n.setStatus("SENT");
            n.setSentAt(now);
        } else {
            n.setRetryCount(n.getRetryCount() + 1);
            n.setLastError(error);
            n.setStatus(n.getRetryCount() >= MAX_RETRIES ? "FAILED" : "PENDING");
        }
        notificationRepository.save(n);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private NotificationDtos.NotificationResponse toResponse(Notification n) {
        return NotificationDtos.NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .channel(n.getChannel())
                .relatedEntityType(n.getRelatedEntityType())
                .relatedEntityId(n.getRelatedEntityId())
                .status(n.getStatus())
                .isRead(n.getIsRead())
                .retryCount(n.getRetryCount())
                .sentAt(n.getSentAt() != null ? n.getSentAt().toString() : null)
                .readAt(n.getReadAt() != null ? n.getReadAt().toString() : null)
                .createdAt(n.getCreatedAt() != null ? n.getCreatedAt().toString() : null)
                .build();
    }
}
