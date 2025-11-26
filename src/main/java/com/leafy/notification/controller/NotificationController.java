package com.leafy.notification.controller;

import com.leafy.notification.scheduler.NotificationScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 테스트 및 관리 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationScheduler notificationScheduler;

    @Operation(summary = "아침 알림 수동 발송 (테스트용)", description = "스케줄러를 기다리지 않고 즉시 알림 발송 로직을 실행합니다.")
    @PostMapping("/send-morning")
    public ResponseEntity<String> sendMorningNotificationsManual() {
        // 스케줄러에 있는 로직을 강제로 호출!
        notificationScheduler.sendMorningNotifications();
        return ResponseEntity.ok("알림 발송 로직이 실행되었습니다. 서버 로그를 확인하세요.");
    }
}