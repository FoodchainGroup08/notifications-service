package com.microservices.notifications_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.email.SmtpMailService;
import com.microservices.notifications_service.service.NotificationLogService;
import com.microservices.notifications_service.websocket.RawWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class NotificationEventConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final SmtpMailService smtpMailService;
    private final NotificationLogService notificationLogService;

    @Autowired
    @Qualifier("kitchenWebSocketHandler")
    private RawWebSocketHandler kitchenWebSocketHandler;

    @Autowired
    @Qualifier("orderWebSocketHandler")
    private RawWebSocketHandler orderWebSocketHandler;

    @Autowired
    @Qualifier("managerWebSocketHandler")
    private RawWebSocketHandler managerWebSocketHandler;

    @Autowired
    public NotificationEventConsumer(SimpMessagingTemplate messagingTemplate,
                                     ObjectMapper objectMapper,
                                     SmtpMailService smtpMailService,
                                     NotificationLogService notificationLogService) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.smtpMailService = smtpMailService;
        this.notificationLogService = notificationLogService;
    }

    @KafkaListener(topics = "order.received", groupId = "notifications-service-group")
    public void handleOrderReceived(String message) {
        try {
            NotificationDtos.OrderReceivedEvent event =
                    objectMapper.readValue(message, NotificationDtos.OrderReceivedEvent.class);

            log.info("Notification: order received orderId={} customerId={}", event.getOrderId(), event.getCustomerId());

            // STOMP push to customer
            NotificationDtos.CustomerNotification notification = NotificationDtos.CustomerNotification.builder()
                    .type("ORDER_RECEIVED")
                    .orderId(event.getOrderId())
                    .title("Order Received!")
                    .message("Your order has been received and is being reviewed by the kitchen.")
                    .status(event.getStatus())
                    .timestamp(LocalDateTime.now())
                    .build();

            pushToCustomer(event.getCustomerId(), notification);
            notificationLogService.logOrderReceived(event, notification);

            // Raw WebSocket broadcast — kitchen queue and manager dashboard
            Map<String, Object> wsPayload = buildWsPayload(
                    event.getOrderId(),
                    event.getBranchId(),
                    event.getCustomerId(),
                    null,
                    event.getStatus(),
                    null
            );
            broadcastToRawWs(event.getBranchId(), null, wsPayload, true, false, true);

        } catch (Exception e) {
            log.error("Failed to process order.received event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "order.status.updated", groupId = "notifications-service-group")
    public void handleOrderStatusUpdated(String message) {
        try {
            NotificationDtos.OrderStatusUpdatedEvent event =
                    objectMapper.readValue(message, NotificationDtos.OrderStatusUpdatedEvent.class);
            String newStatus = coalesce(event.getNewStatus(), event.getStatus());

            log.info("Notification: status update orderId={} {} -> {}",
                    event.getOrderId(), event.getPreviousStatus(), newStatus);

            // STOMP push to customer
            NotificationDtos.CustomerNotification notification = NotificationDtos.CustomerNotification.builder()
                    .type("STATUS_UPDATE")
                    .orderId(event.getOrderId())
                    .title(resolveTitle(newStatus))
                    .message(resolveMessage(newStatus, event.getNotes()))
                    .status(newStatus)
                    .timestamp(LocalDateTime.now())
                    .build();

            pushToCustomer(event.getCustomerId(), notification);
            sendStatusEmail(event, notification);
            notificationLogService.logStatusUpdate(event, notification);

            // Raw WebSocket broadcast — kitchen, order tracker, and manager dashboard
            Map<String, Object> wsPayload = buildWsPayload(
                    event.getOrderId(),
                    event.getBranchId(),
                    event.getCustomerId(),
                    event.getPreviousStatus(),
                    newStatus,
                    event.getUpdatedBy()
            );
            broadcastToRawWs(event.getBranchId(), event.getOrderId(), wsPayload, true, true, true);

        } catch (Exception e) {
            log.error("Failed to process order.status.updated event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "order.ready", groupId = "notifications-service-group")
    public void handleOrderReady(String message) {
        try {
            NotificationDtos.OrderReadyEvent event =
                    objectMapper.readValue(message, NotificationDtos.OrderReadyEvent.class);

            log.info("Notification: order ready orderId={} type={}", event.getOrderId(), event.getOrderType());

            String body = "DINE_IN".equals(event.getOrderType())
                    ? "Your order is ready! It will be brought to table " + event.getTableNumber() + " shortly."
                    : "TAKEAWAY".equals(event.getOrderType())
                    ? "Your order is ready for pickup at the counter."
                    : "Your order is ready and being prepared for delivery.";

            // STOMP push to customer
            NotificationDtos.CustomerNotification notification = NotificationDtos.CustomerNotification.builder()
                    .type("ORDER_READY")
                    .orderId(event.getOrderId())
                    .title("Your Order is Ready!")
                    .message(body)
                    .status("READY")
                    .timestamp(LocalDateTime.now())
                    .build();

            pushToCustomer(event.getCustomerId(), notification);
            notificationLogService.logOrderReady(event, notification);

        } catch (Exception e) {
            log.error("Failed to process order.ready event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "notification.email.send", groupId = "notifications-service-group")
    public void handleEmailSend(String message) {
        NotificationDtos.EmailSendEvent event;
        try {
            event = objectMapper.readValue(message, NotificationDtos.EmailSendEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Invalid notification.email.send JSON: {}", e.getOriginalMessage());
            return;
        }
        log.debug("Email notification event type={} to={}", event.getEmailType(), event.getToEmail());
        smtpMailService.send(event);
        notificationLogService.logEmail(event);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void pushToCustomer(String customerId, NotificationDtos.CustomerNotification notification) {
        if (customerId == null) return;
        messagingTemplate.convertAndSend("/topic/customer/" + customerId, notification);
        log.debug("Pushed {} to /topic/customer/{}", notification.getType(), customerId);
    }

    /**
     * Build the standard raw-WebSocket JSON payload used by frontend hooks.
     */
    private Map<String, Object> buildWsPayload(String orderId, String branchId, String customerId,
                                                String oldStatus, String newStatus, String updatedBy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId",    orderId);
        payload.put("branchId",   branchId);
        payload.put("customerId", customerId);
        payload.put("oldStatus",  oldStatus);
        payload.put("newStatus",  newStatus);
        payload.put("updatedBy",  updatedBy);
        payload.put("timestamp",  Instant.now().toString());
        return payload;
    }

    /**
     * Serialize the payload and broadcast to whichever raw-WS handlers are requested.
     *
     * @param branchId        used as key for kitchen and manager handlers
     * @param orderId         used as key for the order handler
     * @param payload         the map to serialize to JSON
     * @param toKitchen       whether to broadcast to the kitchen handler
     * @param toOrder         whether to broadcast to the order-tracker handler
     * @param toManager       whether to broadcast to the manager handler
     */
    private void broadcastToRawWs(String branchId, String orderId, Map<String, Object> payload,
                                   boolean toKitchen, boolean toOrder, boolean toManager) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            if (toKitchen && branchId != null) {
                kitchenWebSocketHandler.broadcast(branchId, json);
                log.debug("[kitchen] Broadcasted WS event for branchId={}", branchId);
            }
            if (toOrder && orderId != null) {
                orderWebSocketHandler.broadcast(orderId, json);
                log.debug("[order] Broadcasted WS event for orderId={}", orderId);
            }
            if (toManager && branchId != null) {
                managerWebSocketHandler.broadcast(branchId, json);
                log.debug("[manager] Broadcasted WS event for branchId={}", branchId);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize WS payload: {}", e.getMessage());
        }
    }

    private String resolveTitle(String status) {
        return switch (normalizeStatus(status)) {
            case "CONFIRMED" -> "Order Confirmed";
            case "PREPARING" -> "Kitchen is Preparing Your Order";
            case "READY"     -> "Your Order is Ready!";
            case "COMPLETED" -> "Order Completed — Enjoy!";
            case "CANCELLED" -> "Order Cancelled";
            default          -> "Order Update";
        };
    }

    private String resolveMessage(String status, String notes) {
        String normalizedStatus = normalizeStatus(status);
        String base = switch (normalizedStatus) {
            case "CONFIRMED" -> "Your order has been confirmed and sent to the kitchen.";
            case "PREPARING" -> "The kitchen has started preparing your order.";
            case "READY"     -> "Your order is ready and on its way to you.";
            case "COMPLETED" -> "Your order has been completed. Thank you for dining with us!";
            case "CANCELLED" -> "Your order has been cancelled.";
            default          -> "Your order status has been updated to " + normalizedStatus + ".";
        };
        return (notes != null && !notes.isBlank()) ? base + " Note: " + notes : base;
    }

    private void sendStatusEmail(NotificationDtos.OrderStatusUpdatedEvent event,
                                 NotificationDtos.CustomerNotification notification) {
        if (event.getCustomerEmail() == null || event.getCustomerEmail().isBlank()) {
            log.warn("Skip status email for order {}: missing customerEmail", event.getOrderId());
            return;
        }

        String html = """
                <div style="font-family:Arial,sans-serif;line-height:1.5;color:#1f2933">
                  <h2 style="margin:0 0 12px">FoodChain order update</h2>
                  <p>Hello %s,</p>
                  <p>%s</p>
                  <p><strong>Order:</strong> %s<br><strong>Status:</strong> %s</p>
                  <p style="margin-top:24px">Thank you for choosing FoodChain.</p>
                </div>
                """.formatted(
                escapeHtml(coalesce(event.getCustomerName(), "there")),
                escapeHtml(notification.getMessage()),
                escapeHtml(event.getOrderId()),
                escapeHtml(normalizeStatus(notification.getStatus()))
        );

        try {
            smtpMailService.send(NotificationDtos.EmailSendEvent.builder()
                    .toEmail(event.getCustomerEmail())
                    .toName(event.getCustomerName())
                    .subject(notification.getTitle())
                    .htmlContent(html)
                    .emailType("ORDER_STATUS_UPDATE")
                    .build());
        } catch (Exception e) {
            log.error("Failed to send status email for order {} to {}: {}",
                    event.getOrderId(), event.getCustomerEmail(), e.getMessage());
        }
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "UNKNOWN" : status.toUpperCase();
    }

    private String coalesce(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
