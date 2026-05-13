package com.microservices.notifications_service.service;

import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.model.Notification;
import com.microservices.notifications_service.model.NotificationDeliveryAttempt;
import com.microservices.notifications_service.repository.NotificationDeliveryAttemptRepository;
import com.microservices.notifications_service.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDeliveryAttemptRepository deliveryAttemptRepository;
    @Mock private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private NotificationServiceImpl service;

    private static final String USER_ID = "user-001";

    // ── persist ───────────────────────────────────────────────────────────────

    @Test
    void persist_savesNotificationWithPendingStatusAndAttemptsDelivery() {
        Notification saved = buildNotification(1L, "PENDING");

        when(notificationRepository.save(any())).thenReturn(saved);
        when(deliveryAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.persist(
                USER_ID, "Order Received!", "Your order has been received.",
                "ORDER_RECEIVED", "IN_APP", "ORDER", "order-001");

        // Notification persisted
        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(notifCaptor.capture());
        Notification persisted = notifCaptor.getAllValues().get(0);
        assertThat(persisted.getStatus()).isEqualTo("PENDING");
        assertThat(persisted.getIsRead()).isFalse();

        // Delivery attempted (STOMP push)
        verify(messagingTemplate).convertAndSend(contains(USER_ID), any(Object.class));

        // Delivery attempt recorded
        verify(deliveryAttemptRepository).save(any(NotificationDeliveryAttempt.class));
    }

    @Test
    void persist_deliverySuccess_updatesStatusToSent() {
        Notification saved = buildNotification(1L, "PENDING");
        when(notificationRepository.save(any())).thenReturn(saved);
        when(deliveryAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persist(USER_ID, "Title", "Message", "ORDER_RECEIVED", "IN_APP", "ORDER", "order-1");

        // After successful delivery, save is called again with SENT status
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture()); // persist + status update
        Notification updated = captor.getAllValues().get(1);
        assertThat(updated.getStatus()).isEqualTo("SENT");
        assertThat(updated.getSentAt()).isNotNull();
    }

    @Test
    void persist_deliveryFailure_incrementsRetryCountAndSetsPending() {
        Notification saved = buildNotification(1L, "PENDING");
        when(notificationRepository.save(any())).thenReturn(saved);
        when(deliveryAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("WS unavailable"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        service.persist(USER_ID, "Title", "Message", "ORDER_RECEIVED", "IN_APP", "ORDER", "order-1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        Notification updated = captor.getAllValues().get(1);
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getLastError()).contains("WS unavailable");
        assertThat(updated.getStatus()).isEqualTo("PENDING"); // 1st failure → still PENDING, not FAILED
    }

    // ── markRead ──────────────────────────────────────────────────────────────

    @Test
    void markRead_existingUnreadNotification_marksAsRead() {
        Notification n = buildNotification(10L, "SENT");
        n.setIsRead(false);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationDtos.NotificationResponse result = service.markRead(10L, USER_ID);

        assertThat(result.getIsRead()).isTrue();
        assertThat(result.getStatus()).isEqualTo("READ");
        verify(notificationRepository).save(argThat(s -> Boolean.TRUE.equals(s.getIsRead())));
    }

    @Test
    void markRead_notificationNotFound_throws404() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(99L, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markRead_differentUser_throws403() {
        Notification n = buildNotification(5L, "SENT");
        n.setUserId("other-user");
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.markRead(5L, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── markAllRead ───────────────────────────────────────────────────────────

    @Test
    void markAllRead_returnsCountOfUpdatedRows() {
        when(notificationRepository.markAllReadByUserId(eq(USER_ID), any())).thenReturn(5);

        int updated = service.markAllRead(USER_ID);

        assertThat(updated).isEqualTo(5);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownNotification_deletesSuccessfully() {
        Notification n = buildNotification(7L, "SENT");
        when(notificationRepository.findById(7L)).thenReturn(Optional.of(n));

        service.delete(7L, USER_ID);

        verify(notificationRepository).delete(n);
    }

    @Test
    void delete_differentUser_throws403() {
        Notification n = buildNotification(7L, "SENT");
        n.setUserId("someone-else");
        when(notificationRepository.findById(7L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.delete(7L, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── getHistory ────────────────────────────────────────────────────────────

    @Test
    void getHistory_returnsMappedPage() {
        Notification n = buildNotification(1L, "SENT");
        n.setTitle("Order Ready"); n.setType("ORDER_READY"); n.setIsRead(false);
        Page<Notification> page = new PageImpl<>(List.of(n));

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(page);

        Page<NotificationDtos.NotificationResponse> result = service.getHistory(USER_ID, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Order Ready");
        assertThat(result.getContent().get(0).getIsRead()).isFalse();
    }

    // ── getUnreadCount ────────────────────────────────────────────────────────

    @Test
    void getUnreadCount_returnsRepositoryCount() {
        when(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(7L);

        assertThat(service.getUnreadCount(USER_ID)).isEqualTo(7L);
    }

    // ── retryFailed ───────────────────────────────────────────────────────────

    @Test
    void retryFailed_eligibleNotification_retriesDelivery() {
        // Notification with retryCount=1 and updatedAt far enough in the past
        Notification n = buildNotification(20L, "FAILED");
        n.setRetryCount(1);
        n.setUpdatedAt(LocalDateTime.now().minusMinutes(10)); // backoff=2min, so eligible

        when(notificationRepository.findRetryable(anyInt(), any())).thenReturn(List.of(n));
        when(notificationRepository.save(any())).thenReturn(n);
        when(deliveryAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.retryFailed();

        // STOMP push attempted
        verify(messagingTemplate).convertAndSend(contains(USER_ID), any(Object.class));
        // Delivery attempt recorded
        verify(deliveryAttemptRepository).save(any(NotificationDeliveryAttempt.class));
    }

    @Test
    void retryFailed_notYetDueForBackoff_skipsDelivery() {
        // retryCount=2 → backoff=4 min; updatedAt only 1 min ago → not yet eligible
        Notification n = buildNotification(21L, "FAILED");
        n.setRetryCount(2);
        n.setUpdatedAt(LocalDateTime.now().minusMinutes(1));

        when(notificationRepository.findRetryable(anyInt(), any())).thenReturn(List.of(n));

        service.retryFailed();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Notification buildNotification(Long id, String status) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(USER_ID);
        n.setTitle("Test Notification");
        n.setMessage("Test message");
        n.setType("ORDER_RECEIVED");
        n.setChannel("IN_APP");
        n.setRelatedEntityType("ORDER");
        n.setRelatedEntityId("order-001");
        n.setStatus(status);
        n.setIsRead(false);
        n.setRetryCount(0);
        n.setCreatedAt(LocalDateTime.now());
        n.setUpdatedAt(LocalDateTime.now());
        return n;
    }
}
