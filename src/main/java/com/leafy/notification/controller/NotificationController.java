package com.leafy.notification.controller;

import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.diagnosis.repository.DiagnosisHistoryRepository;
import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.notification.domain.Notification;
import com.leafy.notification.repository.NotificationRepository;
import com.leafy.notification.scheduler.NotificationScheduler;
import com.leafy.notification.service.KakaoMessageService;
import com.leafy.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "알림 테스트 및 관리 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationScheduler notificationScheduler;
    private final DiagnosisHistoryRepository diagnosisHistoryRepository;
    private final KakaoMessageService kakaoMessageService;
    private final NotificationRepository notificationRepository;

    @Operation(summary = "아침 알림 수동 발송 (테스트용)", description = "스케줄러를 기다리지 않고 즉시 알림 발송 로직을 실행합니다.")
    @PostMapping("/send-morning")
    public ResponseEntity<String> sendMorningNotificationsManual() {
        // 스케줄러에 있는 로직을 강제로 호출!
        notificationScheduler.sendMorningNotifications();
        return ResponseEntity.ok("알림 발송 로직이 실행되었습니다. 서버 로그를 확인하세요.");
    }

    @Operation(summary = "[테스트] AI Doctor 알림 강제 발송",
            description = "특정 진단 기록(diagnosisId)에 대해 D+2(TIP) 또는 D+5(CHECK) 알림을 즉시 발송합니다.")
    @PostMapping("/test/diagnosis/{diagnosisId}")
    @Transactional
    public ResponseEntity<String> sendTestDiagnosisNotification(
            @PathVariable Long diagnosisId,
            @RequestParam String type // "TIP" or "CHECK"
    ) {
        DiagnosisHistory history = diagnosisHistoryRepository.findById(diagnosisId)
                .orElseThrow(() -> new EntityNotFoundException("진단 기록을 찾을 수 없습니다. ID: " + diagnosisId));

        User user = history.getMyPlant().getUser();
        String message = "";
        String notiType = "";

        if ("TIP".equalsIgnoreCase(type)) {
            message = "[테스트] 💊 관리 팁: 환기를 잘 시켜주세요!";
            notiType = "DIAGNOSIS_TIP";
            // 실제 로직처럼 상태 업데이트도 하려면: history.updateStep(DiagnosisFeedbackStep.TIP_SENT);
        } else if ("CHECK".equalsIgnoreCase(type)) {
            message = "[테스트] 🔍 상태 확인: 5일이 지났습니다. 상태가 어떤가요?";
            notiType = "DIAGNOSIS_CHECK";
            // history.updateStep(DiagnosisFeedbackStep.CHECK_REQUESTED);
        } else {
            return ResponseEntity.badRequest().body("type은 'TIP' 또는 'CHECK'여야 합니다.");
        }

        // 카카오톡 전송
        boolean isSent = kakaoMessageService.sendSelfMessage(user, message);

        // 알림 내역 저장 (프론트에서 알림함 확인용)
        if (isSent) {
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .myPlant(history.getMyPlant())
                    .notificationType(notiType)
                    .message(message)
                    .relatedDiagnosis(history) // 연관관계 설정
                    .isRead(false)
                    .build());
            return ResponseEntity.ok("테스트 알림 발송 성공! (" + type + ")");
        } else {
            return ResponseEntity.status(500).body("카카오 메시지 발송 실패");
        }
    }
}