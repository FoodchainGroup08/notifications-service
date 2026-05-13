package com.microservices.notifications_service.controller;

import com.microservices.notifications_service.entity.NotificationLog;
import com.microservices.notifications_service.service.NotificationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Real-time notifications via raw WebSocket and legacy STOMP/SockJS. " +
        "Raw WS endpoints: /ws/kitchen/{branchId}, /ws/orders/{orderId}, /ws/manager/{branchId}. " +
        "Legacy STOMP: connect to /ws-notifications and subscribe to /topic/customer/{customerId}.")
public class NotificationController {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationLogService notificationLogService;

    @GetMapping("/ws-info")
    @Operation(summary = "WebSocket connection info",
            description = "Returns available WebSocket endpoints for both raw WS (frontend hooks) and legacy STOMP/SockJS.")
    @ApiResponse(responseCode = "200", description = "WebSocket info returned")
    public ResponseEntity<Map<String, Object>> getWsInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("kitchenQueue",   "ws://[host]/ws/kitchen/{branchId}");
        info.put("orderTracker",   "ws://[host]/ws/orders/{orderId}");
        info.put("managerOrders",  "ws://[host]/ws/manager/{branchId}");
        info.put("stompLegacy",    "ws://[host]/ws-notifications (STOMP/SockJS)");
        info.put("messagePayload", Map.of(
                "orderId",    "string",
                "branchId",   "string",
                "customerId", "string",
                "oldStatus",  "string",
                "newStatus",  "string",
                "updatedBy",  "string",
                "timestamp",  "ISO-8601 string"
        ));
        return ResponseEntity.ok(info);
    }

    @GetMapping("/logs")
    @Operation(summary = "Get all notification logs", description = "Returns a paginated list of all sent notifications.")
    @ApiResponse(responseCode = "200", description = "Logs returned")
    public ResponseEntity<Page<NotificationLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationLogService.findAll(PageRequest.of(page, size)));
    }

    @GetMapping("/logs/order/{orderId}")
    @Operation(summary = "Get notification logs by order", description = "Returns all notifications sent for a specific order.")
    @ApiResponse(responseCode = "200", description = "Logs returned")
    public ResponseEntity<Page<NotificationLog>> getLogsByOrder(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationLogService.findByOrderId(orderId, PageRequest.of(page, size)));
    }

    @GetMapping("/logs/customer/{customerId}")
    @Operation(summary = "Get notification logs by customer", description = "Returns all notifications sent to a specific customer.")
    @ApiResponse(responseCode = "200", description = "Logs returned")
    public ResponseEntity<Page<NotificationLog>> getLogsByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationLogService.findByCustomerId(customerId, PageRequest.of(page, size)));
    }
}
