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

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Real-time customer notifications via WebSocket STOMP. " +
        "Connect to /ws-notifications and subscribe to /topic/customer/{customerId} to receive order updates.")
public class NotificationController {

    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/ws-info")
    @Operation(summary = "WebSocket connection info",
            description = "Returns the WebSocket endpoint and topic patterns for client configuration.")
    @ApiResponse(responseCode = "200", description = "WebSocket info returned")
    public ResponseEntity<Map<String, String>> wsInfo() {
        return ResponseEntity.ok(Map.of(
                "endpoint",      "/ws-notifications",
                "customerTopic", "/topic/customer/{customerId}",
                "protocol",      "STOMP over SockJS"
        ));
    }
}
