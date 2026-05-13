package com.microservices.notifications_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.email.BrevoMailService;
import com.microservices.notifications_service.service.NotificationService;
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
    private final BrevoMailService brevoMailService;
    private final NotificationService notificationService;

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
                                     BrevoMailService brevoMailService,
                                     NotificationService notificationService) {
        this.messagingTemplate     = messagingTemplate;
        this.objectMapper          = objectMapper;
        this.brevoMailService      = brevoMailService;
        this.notificationService   = notificationService;
    }

    // ── Kafka consumers ───────────────────────────────────────────────────────

    @KafkaListener(topics = "order.received", groupId = "notifications-service-group")
    public void handleOrderReceived(String message) {
        try {
            NotificationDtos.OrderReceivedEvent event =
                    objectMapper.readValue(message, NotificationDtos.OrderReceivedEvent.class);
            log.info("Notification: order received orderId={} customerId={}", event.getOrderId(), event.getCustomerId());

            // 1. Persist IN_APP notification (delivers immediately via STOMP inside service)
            notificationService.persist(
                    event.getCustomerId(),
                    "Order Received!",
                    "Your order has been received and is being reviewed by the kitchen.",
                    "ORDER_RECEIVED", "IN_APP", "ORDER", event.getOrderId());

            // 2. Email
            sendOrderReceivedEmail(event);

            // 3. Raw WebSocket broadcast for kitchen + manager dashboards
            Map<String, Object> wsPayload = buildWsPayload(
                    event.getOrderId(), event.getBranchId(), event.getCustomerId(),
                    null, event.getStatus(), null);
            broadcastToRawWs(event.getBranchId(), null, wsPayload, true, false, true);

        } catch (Exception e) {
            log.error("Failed to process order.received event: {}", e.getMessage(), e);
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

            String title   = resolveTitle(newStatus);
            String body    = resolveMessage(newStatus, event.getNotes());

            // 1. Persist IN_APP notification
            notificationService.persist(
                    event.getCustomerId(), title, body,
                    "STATUS_UPDATE", "IN_APP", "ORDER", event.getOrderId());

            // 2. Email
            NotificationDtos.CustomerNotification notif = NotificationDtos.CustomerNotification.builder()
                    .type("STATUS_UPDATE").orderId(event.getOrderId())
                    .title(title).message(body).status(newStatus)
                    .timestamp(LocalDateTime.now()).build();
            sendStatusEmail(event, notif);

            // 3. Raw WebSocket broadcast
            Map<String, Object> wsPayload = buildWsPayload(
                    event.getOrderId(), event.getBranchId(), event.getCustomerId(),
                    event.getPreviousStatus(), newStatus, event.getUpdatedBy());
            broadcastToRawWs(event.getBranchId(), event.getOrderId(), wsPayload, true, true, true);

        } catch (Exception e) {
            log.error("Failed to process order.status.updated event: {}", e.getMessage(), e);
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

            // 1. Persist IN_APP notification
            notificationService.persist(
                    event.getCustomerId(),
                    "Your Order is Ready!", body,
                    "ORDER_READY", "IN_APP", "ORDER", event.getOrderId());

            // 2. Email — send for order-ready as well
            if (event.getCustomerEmail() != null && !event.getCustomerEmail().isBlank()) {
                sendOrderReadyEmail(event, body);
            }

        } catch (Exception e) {
            log.error("Failed to process order.ready event: {}", e.getMessage(), e);
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
        brevoMailService.send(event);
    }

    @KafkaListener(topics = "analytics.report.generated", groupId = "notifications-service-group")
    public void handleReportGenerated(String message) {
        try {
            NotificationDtos.ReportGeneratedEvent event =
                    objectMapper.readValue(message, NotificationDtos.ReportGeneratedEvent.class);
            log.info("Notification: report generated reportId={} type={}", event.getReportId(), event.getReportType());

            String title = "Report Generated";
            String body  = String.format("Your %s report (%s to %s) is ready.",
                    event.getReportType(), event.getStartDate(), event.getEndDate());

            // Notify the user who requested it
            if (event.getGeneratedBy() != null && !event.getGeneratedBy().isBlank()) {
                notificationService.persist(
                        event.getGeneratedBy(), title, body,
                        "REPORT_GENERATED", "IN_APP", "REPORT", event.getReportId());
            }
        } catch (Exception e) {
            log.error("Failed to process analytics.report.generated event: {}", e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void pushToCustomer(String customerId, NotificationDtos.CustomerNotification notification) {
        if (customerId == null) return;
        messagingTemplate.convertAndSend("/topic/customer/" + customerId, notification);
        log.debug("Pushed {} to /topic/customer/{}", notification.getType(), customerId);
    }

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

    private void broadcastToRawWs(String branchId, String orderId, Map<String, Object> payload,
                                   boolean toKitchen, boolean toOrder, boolean toManager) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (toKitchen && branchId != null)  kitchenWebSocketHandler.broadcast(branchId, json);
            if (toOrder   && orderId  != null)  orderWebSocketHandler.broadcast(orderId, json);
            if (toManager && branchId != null)  managerWebSocketHandler.broadcast(branchId, json);
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
        String base = switch (normalizeStatus(status)) {
            case "CONFIRMED" -> "Your order has been confirmed and sent to the kitchen.";
            case "PREPARING" -> "The kitchen has started preparing your order.";
            case "READY"     -> "Your order is ready and on its way to you.";
            case "COMPLETED" -> "Your order has been completed. Thank you for dining with us!";
            case "CANCELLED" -> "Your order has been cancelled.";
            default          -> "Your order status has been updated to " + normalizeStatus(status) + ".";
        };
        return (notes != null && !notes.isBlank()) ? base + " Note: " + notes : base;
    }

    private void sendOrderReceivedEmail(NotificationDtos.OrderReceivedEvent event) {
        if (event.getCustomerEmail() == null || event.getCustomerEmail().isBlank()) {
            log.warn("Skip order-received email for order {}: missing customerEmail", event.getOrderId());
            return;
        }

        StringBuilder itemRows = new StringBuilder();
        if (event.getItems() != null) {
            for (NotificationDtos.OrderItemEvent item : event.getItems()) {
                itemRows.append(String.format(
                        "<tr><td style=\"padding:6px 8px;border-bottom:1px solid #eee\">%s</td>"
                        + "<td style=\"padding:6px 8px;border-bottom:1px solid #eee;text-align:center\">%d</td></tr>",
                        escapeHtml(item.getMenuItemName()), item.getQuantity()));
            }
        }

        String html = """
                <div style="font-family:Arial,sans-serif;line-height:1.6;color:#1f2933;max-width:560px">
                  <div style="background:#e85d04;padding:24px 32px;border-radius:8px 8px 0 0">
                    <h1 style="color:#fff;margin:0;font-size:22px">FoodChain</h1>
                  </div>
                  <div style="background:#fff;padding:32px;border:1px solid #eee;border-top:none;border-radius:0 0 8px 8px">
                    <h2 style="margin-top:0;color:#222">Order Received!</h2>
                    <p>Hi %s,</p>
                    <p>We've received your order and the kitchen will start on it shortly.</p>
                    <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin:16px 0">
                      <thead>
                        <tr style="background:#f9f9f9">
                          <th style="padding:8px;text-align:left;border-bottom:2px solid #eee">Item</th>
                          <th style="padding:8px;text-align:center;border-bottom:2px solid #eee">Qty</th>
                        </tr>
                      </thead>
                      <tbody>%s</tbody>
                    </table>
                    <p><strong>Order ID:</strong> %s</p>
                    <p style="margin-top:24px;color:#999;font-size:13px">Thank you for choosing FoodChain.</p>
                  </div>
                </div>
                """.formatted(
                escapeHtml(coalesce(event.getCustomerName(), "there")),
                itemRows.toString(),
                escapeHtml(event.getOrderId()));

        sendEmailSafely(event.getCustomerEmail(), event.getCustomerName(),
                "FoodChain — Order Received", html, "ORDER_RECEIVED");
    }

    private void sendStatusEmail(NotificationDtos.OrderStatusUpdatedEvent event,
                                 NotificationDtos.CustomerNotification notification) {
        if (event.getCustomerEmail() == null || event.getCustomerEmail().isBlank()) return;

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
                escapeHtml(normalizeStatus(notification.getStatus())));

        sendEmailSafely(event.getCustomerEmail(), event.getCustomerName(),
                notification.getTitle(), html, "ORDER_STATUS_UPDATE");
    }

    private void sendOrderReadyEmail(NotificationDtos.OrderReadyEvent event, String body) {
        String html = """
                <div style="font-family:Arial,sans-serif;line-height:1.5;color:#1f2933">
                  <h2 style="margin:0 0 12px">FoodChain — Your Order is Ready!</h2>
                  <p>Hi %s,</p>
                  <p>%s</p>
                  <p><strong>Order ID:</strong> %s</p>
                  <p style="margin-top:24px">Thank you for choosing FoodChain.</p>
                </div>
                """.formatted(
                escapeHtml(coalesce(event.getCustomerName(), "there")),
                escapeHtml(body),
                escapeHtml(event.getOrderId()));

        sendEmailSafely(event.getCustomerEmail(), event.getCustomerName(),
                "FoodChain — Your Order is Ready!", html, "ORDER_READY");
    }

    private void sendEmailSafely(String toEmail, String toName, String subject,
                                  String html, String emailType) {
        try {
            brevoMailService.send(NotificationDtos.EmailSendEvent.builder()
                    .toEmail(toEmail).toName(toName)
                    .subject(subject).htmlContent(html)
                    .emailType(emailType)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send {} email to {}: {}", emailType, toEmail, e.getMessage());
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
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
