package com.microservices.notifications_service.service;

import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.model.Notification;
import org.springframework.data.domain.Page;

public interface NotificationService {

    /** Persist a notification with status=PENDING, then attempt delivery immediately. */
    Notification persist(String userId, String title, String message,
                         String type, String channel,
                         String relatedEntityType, String relatedEntityId);

    /** Mark a specific notification as read for the given user. */
    NotificationDtos.NotificationResponse markRead(Long notificationId, String userId);

    /** Mark all unread notifications as read for a user. */
    int markAllRead(String userId);

    /** Delete a notification owned by the given user. */
    void delete(Long notificationId, String userId);

    /** Paginated history for a user. */
    Page<NotificationDtos.NotificationResponse> getHistory(String userId, int page, int size);

    /** Count of unread notifications for a user. */
    long getUnreadCount(String userId);

    /** Called by the retry scheduler — re-attempts delivery for eligible FAILED notifications. */
    void retryFailed();
}
