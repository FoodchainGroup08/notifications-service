package com.microservices.notifications_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class NotificationDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderReceivedEvent {
        private String orderId;
        private String customerId;
        private String branchId;
        private String status;
        private BigDecimal totalAmount;
        private String orderType;
        private String tableNumber;
        private String notes;
        private List<OrderItemEvent> items;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderStatusUpdatedEvent {
        private String orderId;
        private String customerId;
        private String branchId;
        private String previousStatus;
        private String newStatus;
        private String updatedBy;
        private String notes;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderReadyEvent {
        private String orderId;
        private String customerId;
        private String branchId;
        private String orderType;
        private String tableNumber;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItemEvent {
        private String menuItemId;
        private String menuItemName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private String specialInstructions;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CustomerNotification {
        private String type;
        private String orderId;
        private String title;
        private String message;
        private String status;
        private LocalDateTime timestamp;
    }
}
