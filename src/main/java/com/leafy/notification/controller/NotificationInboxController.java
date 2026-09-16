package com.leafy.notification.controller;

import com.leafy.notification.dto.NotificationResponse;
import com.leafy.notification.service.NotificationInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Notification Inbox", description = "앱 안 알림함")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationInboxController {

    private final NotificationInboxService inboxService;

    @Operation(summary = "내 알림 목록 (최신순)")
    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal UserDetails userDetails) {
        return inboxService.list(userDetails.getUsername());
    }

    @Operation(summary = "안 읽은 알림 개수")
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserDetails userDetails) {
        return Map.of("count", inboxService.unreadCount(userDetails.getUsername()));
    }

    @Operation(summary = "알림 하나 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal UserDetails userDetails,
                                           @PathVariable Long notificationId) {
        inboxService.markAsRead(userDetails.getUsername(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모든 알림 읽음 처리")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal UserDetails userDetails) {
        inboxService.markAllAsRead(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
