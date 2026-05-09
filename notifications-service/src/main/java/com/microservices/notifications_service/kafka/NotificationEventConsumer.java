package com.microservices.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.notification.dto.NotificationDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.received", groupId = "notification-service-group")
    public void handleOrderReceived(String message) {
        try {
            NotificationDtos.OrderReceivedEvent event =
                    objectMapper.readValue(message, NotificationDtos.OrderReceivedEvent.class);

            log.info("Notification: order received orderId={} customerId={}", event.getOrderId(), event.getCustomerId());

            NotificationDtos.CustomerNotification notification = NotificationDtos.CustomerNotification.builder()
                    .type("ORDER_RECEIVED")
                    .orderId(event.getOrderId())
                    .title("Order Received!")
                    .message("Your order has been received and is being reviewed by the kitchen.")
                    .status(event.getStatus())
                    .timestamp(LocalDateTime.now())
                    .build();

            pushToCustomer(event.getCustomerId(), notification);
        } catch (Exception e) {
            log.error("Failed to process order.received event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "order.status.updated", groupId = "notification-service-group")
    public void handleOrderStatusUpdated(String message) {
        try {
            NotificationDtos.OrderStatusUpdatedEvent event =
                    objectMapper.readValue(message, NotificationDtos.OrderStatusUpdatedEvent.class);

            log.info("Notification: status update orderId={} {} -> {}",
                    event.getOrderId(), event.getPreviousStatus(), event.getNewStatus());

            NotificationDtos.CustomerNotification notification = NotificationDtos.CustomerNotification.builder()
                    .type("STATUS_UPDATE")
                    .orderId(event.getOrderId())
                    .title(resolveTitle(event.getNewStatus()))
                    .message(resolveMessage(event.getNewStatus(), event.getNotes()))
                    .status(event.getNewStatus())
                    .timestamp(LocalDateTime.now())
                    .build();

            pushToCustomer(event.getCustomerId(), notification);
        } catch (Exception e) {
            log.error("Failed to process order.status.updated event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "order.ready", groupId = "notification-service-group")
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

            NotificationDtos.CustomerNotification notification = NotificationDtos.CustomerNotification.builder()
                    .type("ORDER_READY")
                    .orderId(event.getOrderId())
                    .title("Your Order is Ready!")
                    .message(body)
                    .status("READY")
                    .timestamp(LocalDateTime.now())
                    .build();

            pushToCustomer(event.getCustomerId(), notification);
        } catch (Exception e) {
            log.error("Failed to process order.ready event: {}", e.getMessage());
        }
    }

    private void pushToCustomer(String customerId, NotificationDtos.CustomerNotification notification) {
        if (customerId == null) return;
        messagingTemplate.convertAndSend("/topic/customer/" + customerId, notification);
        log.debug("Pushed {} to /topic/customer/{}", notification.getType(), customerId);
    }

    private String resolveTitle(String status) {
        return switch (status) {
            case "CONFIRMED" -> "Order Confirmed";
            case "PREPARING" -> "Kitchen is Preparing Your Order";
            case "READY"     -> "Your Order is Ready!";
            case "COMPLETED" -> "Order Completed — Enjoy!";
            case "CANCELLED" -> "Order Cancelled";
            default          -> "Order Update";
        };
    }

    private String resolveMessage(String status, String notes) {
        String base = switch (status) {
            case "CONFIRMED" -> "Your order has been confirmed and sent to the kitchen.";
            case "PREPARING" -> "The kitchen has started preparing your order.";
            case "READY"     -> "Your order is ready and on its way to you.";
            case "COMPLETED" -> "Your order has been completed. Thank you for dining with us!";
            case "CANCELLED" -> "Your order has been cancelled.";
            default          -> "Your order status has been updated to " + status + ".";
        };
        return (notes != null && !notes.isBlank()) ? base + " Note: " + notes : base;
    }
}
