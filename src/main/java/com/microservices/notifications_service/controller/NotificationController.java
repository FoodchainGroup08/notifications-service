package com.microservices.notifications_service.controller;

import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.entity.NotificationLog;
import com.microservices.notifications_service.service.NotificationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications",
     description = "Notification history and management. Real-time delivery via raw WebSocket and STOMP/SockJS. " +
                   "Raw WS endpoints: /ws/kitchen/{branchId}, /ws/orders/{orderId}, /ws/manager/{branchId}. " +
                   "Legacy STOMP: connect to /ws-notifications then subscribe to /topic/customer/{customerId}.")
public class NotificationController {

    private final NotificationLogService notificationLogService;

    // ── WebSocket info ────────────────────────────────────────────────────────

    @GetMapping("/ws-info")
    @Operation(summary = "WebSocket connection info",
               description = "Returns available WebSocket endpoints for both raw WS and legacy STOMP/SockJS.")
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

    // ── Notification history (authenticated user) ─────────────────────────────

    @GetMapping
    @Operation(summary = "Get notification history",
               description = "Returns paginated notification history for the authenticated user. Newest first.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "200", description = "Notification list")
    public ResponseEntity<Page<NotificationDtos.NotificationHistoryResponse>> getNotifications(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        log.info("GET /notifications userId={} page={}", userId, page);
        return ResponseEntity.ok(notificationLogService.getHistory(userId, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count",
               description = "Returns the number of unread notifications for the authenticated user.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<Long> getUnreadCount(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        long count = notificationLogService.getUnreadCount(userId);
        log.debug("GET /notifications/unread-count userId={} count={}", userId, count);
        return ResponseEntity.ok(count);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<NotificationDtos.NotificationHistoryResponse> markRead(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long id) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        log.info("PATCH /notifications/{}/read userId={}", id, userId);
        return ResponseEntity.ok(notificationLogService.markRead(id, userId));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<Integer> markAllRead(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        log.info("PATCH /notifications/read-all userId={}", userId);
        int updated = notificationLogService.markAllRead(userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<Void> deleteNotification(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long id) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        log.info("DELETE /notifications/{} userId={}", id, userId);
        notificationLogService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Audit log endpoints (admin/ops) ───────────────────────────────────────

    @GetMapping("/logs")
    @Operation(summary = "Get all notification logs",
               description = "Returns a paginated list of all sent notifications (admin/ops use).",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "200", description = "Logs returned")
    public ResponseEntity<Page<NotificationLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationLogService.findAll(PageRequest.of(page, size)));
    }

    @GetMapping("/logs/order/{orderId}")
    @Operation(summary = "Get notification logs by order",
               description = "Returns all notifications sent for a specific order.")
    @ApiResponse(responseCode = "200", description = "Logs returned")
    public ResponseEntity<Page<NotificationLog>> getLogsByOrder(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationLogService.findByOrderId(orderId, PageRequest.of(page, size)));
    }

    @GetMapping("/logs/customer/{customerId}")
    @Operation(summary = "Get notification logs by customer",
               description = "Returns all notifications sent to a specific customer.")
    @ApiResponse(responseCode = "200", description = "Logs returned")
    public ResponseEntity<Page<NotificationLog>> getLogsByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationLogService.findByCustomerId(customerId, PageRequest.of(page, size)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String resolveUserId(Authentication auth, String headerFallback) {
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()
                && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return (headerFallback != null && !headerFallback.isBlank()) ? headerFallback : null;
    }
}
