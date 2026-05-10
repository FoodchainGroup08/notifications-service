package com.microservices.notifications_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
