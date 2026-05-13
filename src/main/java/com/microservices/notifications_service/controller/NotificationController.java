package com.microservices.notifications_service.controller;

import com.microservices.notifications_service.dto.BaseResponse;
import com.microservices.notifications_service.dto.NotificationDtos;
import com.microservices.notifications_service.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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

    private final NotificationService notificationService;

    // ── WebSocket info — kept for backward compatibility ──────────────────────

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

    // ── Notification history ──────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get notification history",
               description = "Returns paginated notification history for the authenticated user. Newest first.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "200", description = "Notification list")
    public ResponseEntity<BaseResponse<Page<NotificationDtos.NotificationResponse>>> getNotifications(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        log.info("GET /notifications userId={} page={}", userId, page);
        Page<NotificationDtos.NotificationResponse> result = notificationService.getHistory(userId, page, size);
        return ResponseEntity.ok(BaseResponse.ok(result, "Notifications retrieved"));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count",
               description = "Returns the number of unread notifications for the authenticated user.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<BaseResponse<Long>> getUnreadCount(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        long count = notificationService.getUnreadCount(userId);
        log.debug("GET /notifications/unread-count userId={} count={}", userId, count);
        return ResponseEntity.ok(BaseResponse.ok(count, "Unread count retrieved"));
    }

    // ── Mutation endpoints ────────────────────────────────────────────────────

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<BaseResponse<NotificationDtos.NotificationResponse>> markRead(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long id) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        log.info("PATCH /notifications/{}/read userId={}", id, userId);
        NotificationDtos.NotificationResponse updated = notificationService.markRead(id, userId);
        return ResponseEntity.ok(BaseResponse.ok(updated, "Notification marked as read"));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<BaseResponse<Integer>> markAllRead(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        log.info("PATCH /notifications/read-all userId={}", userId);
        int updated = notificationService.markAllRead(userId);
        return ResponseEntity.ok(BaseResponse.ok(updated, updated + " notification(s) marked as read"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<BaseResponse<Void>> deleteNotification(
            Authentication auth,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long id) {
        String userId = resolveUserId(auth, userIdHeader);
        if (userId == null) return ResponseEntity.badRequest().build();
        log.info("DELETE /notifications/{} userId={}", id, userId);
        notificationService.delete(id, userId);
        return ResponseEntity.ok(BaseResponse.ok(null, "Notification deleted"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Resolves userId from Spring Security principal name (set by GatewayAuthenticationFilter
     * using X-User-Id header) or falls back to the explicit header for cases where auth context
     * is not propagated (e.g. direct service calls in tests).
     */
    private String resolveUserId(Authentication auth, String headerFallback) {
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()
                && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return (headerFallback != null && !headerFallback.isBlank()) ? headerFallback : null;
    }
}
