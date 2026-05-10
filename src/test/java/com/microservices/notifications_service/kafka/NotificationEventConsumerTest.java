package com.microservices.notifications_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.email.BrevoMailService;
import com.microservices.notifications_service.websocket.RawWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private BrevoMailService brevoMailService;

    @Mock
    private RawWebSocketHandler kitchenWebSocketHandler;

    @Mock
    private RawWebSocketHandler orderWebSocketHandler;

    @Mock
    private RawWebSocketHandler managerWebSocketHandler;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(messagingTemplate, new ObjectMapper(), brevoMailService);
        ReflectionTestUtils.setField(consumer, "kitchenWebSocketHandler", kitchenWebSocketHandler);
        ReflectionTestUtils.setField(consumer, "orderWebSocketHandler", orderWebSocketHandler);
        ReflectionTestUtils.setField(consumer, "managerWebSocketHandler", managerWebSocketHandler);
    }

    // ---------------------------------------------------------------------------
    // handleEmailSend tests
    // ---------------------------------------------------------------------------

    @Test
    void handleEmailSend_callsBrevoMailService_forValidJson() {
        String message = """
                {
                  "toEmail": "customer@example.com",
                  "toName": "John Doe",
                  "subject": "Your order is confirmed",
                  "htmlContent": "<p>Thank you for your order!</p>",
                  "emailType": "ORDER_CONFIRMATION"
                }
                """;

        consumer.handleEmailSend(message);

        ArgumentCaptor<NotificationDtos.EmailSendEvent> captor =
                ArgumentCaptor.forClass(NotificationDtos.EmailSendEvent.class);
        verify(brevoMailService).send(captor.capture());
        NotificationDtos.EmailSendEvent captured = captor.getValue();
        assertThat(captured.getToEmail()).isEqualTo("customer@example.com");
        assertThat(captured.getToName()).isEqualTo("John Doe");
        assertThat(captured.getEmailType()).isEqualTo("ORDER_CONFIRMATION");
    }

    @Test
    void handleEmailSend_logsError_forInvalidJson() {
        consumer.handleEmailSend("not-json");

        verifyNoInteractions(brevoMailService);
    }

    @Test
    void handleEmailSend_logsError_forMalformedJson() {
        consumer.handleEmailSend("{\"toEmail\": }");

        verifyNoInteractions(brevoMailService);
    }

    @Test
    void handleEmailSend_doesNotThrow_whenBrevoThrows() {
        doThrow(new RuntimeException("Brevo is down"))
                .when(brevoMailService).send(any(NotificationDtos.EmailSendEvent.class));

        String message = """
                {
                  "toEmail": "customer@example.com",
                  "htmlContent": "<p>Hello</p>",
                  "emailType": "WELCOME"
                }
                """;

        assertThatNoException().isThrownBy(() -> consumer.handleEmailSend(message));
    }

    @Test
    void handleEmailSend_ignoresUnknownFields_inJson() {
        String message = """
                {
                  "toEmail": "customer@example.com",
                  "htmlContent": "<p>Hello</p>",
                  "emailType": "WELCOME",
                  "unexpectedField": "should be ignored"
                }
                """;

        consumer.handleEmailSend(message);

        verify(brevoMailService).send(any(NotificationDtos.EmailSendEvent.class));
    }

    // ---------------------------------------------------------------------------
    // handleOrderReceived tests
    // ---------------------------------------------------------------------------

    @Test
    void handleOrderReceived_pushesToStomp_forValidEvent() {
        String message = """
                {
                  "orderId": "order-001",
                  "customerId": "cust-123",
                  "branchId": "branch-456",
                  "status": "RECEIVED",
                  "totalAmount": 29.99,
                  "orderType": "DINE_IN",
                  "tableNumber": "5"
                }
                """;

        consumer.handleOrderReceived(message);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/customer/cust-123"),
                any(NotificationDtos.CustomerNotification.class)
        );
    }

    @Test
    void handleOrderReceived_broadcastsToKitchenAndManager_forValidEvent() {
        String message = """
                {
                  "orderId": "order-001",
                  "customerId": "cust-123",
                  "branchId": "branch-456",
                  "status": "RECEIVED",
                  "orderType": "DINE_IN"
                }
                """;

        consumer.handleOrderReceived(message);

        verify(kitchenWebSocketHandler).broadcast(eq("branch-456"), anyString());
        verify(managerWebSocketHandler).broadcast(eq("branch-456"), anyString());
        // handleOrderReceived sets toOrder=false, so order handler should not be called
        verify(orderWebSocketHandler, never()).broadcast(anyString(), anyString());
    }

    @Test
    void handleOrderReceived_logsError_forInvalidJson() {
        consumer.handleOrderReceived("not-json");

        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(kitchenWebSocketHandler);
        verifyNoInteractions(orderWebSocketHandler);
        verifyNoInteractions(managerWebSocketHandler);
    }

    @Test
    void handleOrderReceived_doesNotPushToStomp_whenCustomerIdIsNull() {
        String message = """
                {
                  "orderId": "order-001",
                  "customerId": null,
                  "branchId": "branch-456",
                  "status": "RECEIVED"
                }
                """;

        assertThatNoException().isThrownBy(() -> consumer.handleOrderReceived(message));
        verifyNoInteractions(messagingTemplate);
    }

    // ---------------------------------------------------------------------------
    // handleOrderStatusUpdated tests
    // ---------------------------------------------------------------------------

    @Test
    void handleOrderStatusUpdated_pushesToStomp_forValidEvent() {
        String message = """
                {
                  "orderId": "order-001",
                  "customerId": "cust-123",
                  "branchId": "branch-456",
                  "previousStatus": "RECEIVED",
                  "newStatus": "CONFIRMED",
                  "updatedBy": "staff-001"
                }
                """;

        consumer.handleOrderStatusUpdated(message);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/customer/cust-123"),
                any(NotificationDtos.CustomerNotification.class)
        );
    }

    @Test
    void handleOrderStatusUpdated_broadcastsToAllHandlers_forValidEvent() {
        String message = """
                {
                  "orderId": "order-001",
                  "customerId": "cust-123",
                  "branchId": "branch-456",
                  "previousStatus": "RECEIVED",
                  "newStatus": "PREPARING",
                  "updatedBy": "staff-001"
                }
                """;

        consumer.handleOrderStatusUpdated(message);

        verify(kitchenWebSocketHandler).broadcast(eq("branch-456"), anyString());
        verify(orderWebSocketHandler).broadcast(eq("order-001"), anyString());
        verify(managerWebSocketHandler).broadcast(eq("branch-456"), anyString());
    }

    @Test
    void handleOrderStatusUpdated_logsError_forInvalidJson() {
        consumer.handleOrderStatusUpdated("{bad json}");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleOrderStatusUpdated_resolvesCorrectTitle_forCancelledStatus() throws Exception {
        String message = """
                {
                  "orderId": "order-001",
                  "customerId": "cust-123",
                  "branchId": "branch-456",
                  "previousStatus": "CONFIRMED",
                  "newStatus": "CANCELLED"
                }
                """;

        consumer.handleOrderStatusUpdated(message);

        ArgumentCaptor<NotificationDtos.CustomerNotification> captor =
                ArgumentCaptor.forClass(NotificationDtos.CustomerNotification.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Order Cancelled");
    }

    @Test
    void handleOrderStatusUpdated_appendsNotes_whenNotesPresent() {
        String message = """
                {
                  "orderId": "order-002",
                  "customerId": "cust-999",
                  "branchId": "branch-100",
                  "previousStatus": "RECEIVED",
                  "newStatus": "CANCELLED",
                  "notes": "Out of stock"
                }
                """;

        consumer.handleOrderStatusUpdated(message);

        ArgumentCaptor<NotificationDtos.CustomerNotification> captor =
                ArgumentCaptor.forClass(NotificationDtos.CustomerNotification.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());
        assertThat(captor.getValue().getMessage()).contains("Out of stock");
    }

    // ---------------------------------------------------------------------------
    // handleOrderReady tests
    // ---------------------------------------------------------------------------

    @Test
    void handleOrderReady_pushesToStomp_forValidEvent() {
        String message = """
                {
                  "orderId": "order-001",
                  "customerId": "cust-123",
                  "branchId": "branch-456",
                  "orderType": "DINE_IN",
                  "tableNumber": "7"
                }
                """;

        consumer.handleOrderReady(message);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/customer/cust-123"),
                any(NotificationDtos.CustomerNotification.class)
        );
    }

    @Test
    void handleOrderReady_includesTableNumber_forDineIn() {
        String message = """
                {
                  "orderId": "order-001",
                  "customerId": "cust-123",
                  "branchId": "branch-456",
                  "orderType": "DINE_IN",
                  "tableNumber": "7"
                }
                """;

        consumer.handleOrderReady(message);

        ArgumentCaptor<NotificationDtos.CustomerNotification> captor =
                ArgumentCaptor.forClass(NotificationDtos.CustomerNotification.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());
        assertThat(captor.getValue().getMessage()).contains("table 7");
    }

    @Test
    void handleOrderReady_returnsPickupMessage_forTakeaway() {
        String message = """
                {
                  "orderId": "order-002",
                  "customerId": "cust-456",
                  "branchId": "branch-456",
                  "orderType": "TAKEAWAY"
                }
                """;

        consumer.handleOrderReady(message);

        ArgumentCaptor<NotificationDtos.CustomerNotification> captor =
                ArgumentCaptor.forClass(NotificationDtos.CustomerNotification.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());
        assertThat(captor.getValue().getMessage()).contains("pickup");
    }

    @Test
    void handleOrderReady_logsError_forInvalidJson() {
        consumer.handleOrderReady("not-json");

        verifyNoInteractions(messagingTemplate);
    }
}
