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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    // ✨ [테스트 API] AI Doctor 알림 강제 발송 (랜덤 템플릿 적용됨)
    @Operation(summary = "[테스트] AI Doctor 알림 강제 발송",
            description = "특정 진단 기록(diagnosisId)에 대해 D+2(TIP) 또는 D+5(CHECK) 알림을 즉시 발송합니다.")
    @PostMapping("/test/diagnosis/{diagnosisId}")
    @Transactional
    public ResponseEntity<String> sendTestDiagnosisNotification(
            @PathVariable Long diagnosisId,
            @RequestParam String type // "TIP" or "CHECK"
    ) {
        // 1. 진단 기록 조회
        DiagnosisHistory history = diagnosisHistoryRepository.findById(diagnosisId)
                .orElseThrow(() -> new EntityNotFoundException("진단 기록을 찾을 수 없습니다. ID: " + diagnosisId));

        User user = history.getMyPlant().getUser();
        String plantName = history.getMyPlant().getNickname();
        // ✨ 병명이 없으면 '증상'이라고 표시
        String diseaseName = history.getDiseaseName() != null ? history.getDiseaseName() : "증상";

        String message = "";
        String notiType = "";

        // 2. 타입별 메시지 설정 (랜덤 템플릿 적용)
        if ("TIP".equalsIgnoreCase(type)) {
            String body = getRandomTipTemplate(plantName, diseaseName); // 헬퍼 호출
            message = String.format("💊 [Leafy 닥터] 관리 팁 도착!\n\n%s", body);
            notiType = "DIAGNOSIS_TIP";
        } else if ("CHECK".equalsIgnoreCase(type)) {
            String body = getRandomCheckTemplate(plantName, diseaseName); // 헬퍼 호출
            message = String.format("🔍 [Leafy 닥터] 상태 확인\n\n%s\n\n👇 아래 버튼을 눌러 상태를 기록해주세요!", body);
            notiType = "DIAGNOSIS_CHECK";
        } else {
            return ResponseEntity.badRequest().body("type은 'TIP' 또는 'CHECK'여야 합니다.");
        }

        // 3. 카카오톡 전송
        boolean isSent = kakaoMessageService.sendSelfMessage(user, message);

        // 4. 알림 내역 저장
        if (isSent) {
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .myPlant(history.getMyPlant())
                    .notificationType(notiType)
                    .message(message)
                    .relatedDiagnosis(history)
                    .isRead(false)
                    .build());
            return ResponseEntity.ok("테스트 알림 발송 성공! (" + type + ")\n내용: " + message);
        } else {
            return ResponseEntity.status(500).body("카카오 메시지 발송 실패");
        }
    }

    // --- [테스트용] 템플릿 복사본 (Scheduler와 동일한 로직) ---
    private String getRandomTipTemplate(String nickname, String disease) {
        List<String> templates = new ArrayList<>();
        templates.add("'" + nickname + "'의 " + disease + " 치료는 시작하셨나요?\n약제를 뿌린 후에는 '환기'가 필수입니다! 🌬️ 창문을 활짝 열어주세요.");
        templates.add("'" + nickname + "'가 많이 아파서 속상하시죠? 🥺\n집사님의 정성 어린 치료와 따뜻한 말 한마디면 금방 나을 거예요!");
        templates.add("병해충 퇴치 꿀팁! 🍯\n약제 살포 후 잎이 젖어있을 땐 강한 햇빛을 피하고, 바람이 잘 통하는 곳에 두세요.");
        templates.add("치료 중에는 식물도 예민해요. 💧\n'" + nickname + "'의 흙 상태를 손가락으로 꼭 확인하고 물을 주세요. 과습은 금물!");
        templates.add("'" + nickname + "'의 잎을 깨끗이 닦아주셨나요? ✨\n잎에 쌓인 먼지만 제거해도 숨쉬기가 훨씬 편해진답니다.");
        return templates.get(new Random().nextInt(templates.size()));
    }

    private String getRandomCheckTemplate(String nickname, String disease) {
        List<String> templates = new ArrayList<>();
        templates.add("👨‍⚕️ 닥터 리피 회진 시간입니다.\n'" + nickname + "'의 " + disease + " 증상은 좀 가라앉았나요? 상태를 알려주세요.");
        templates.add("치료를 시작한 지 벌써 5일이 지났네요.\n'" + nickname + "'는 다시 건강을 되찾고 있을까요? 너무 궁금해요! 👀");
        templates.add("아직도 '" + nickname + "'가 힘들어하고 있진 않나요? 🏥\n상태가 호전되지 않았다면 재진단이 필요할 수도 있어요.");
        templates.add("꾸준한 관찰이 식물을 살립니다! 📝\n지난번 진단 이후 '" + nickname + "'에게 어떤 변화가 있었는지 기록해주세요.");
        templates.add("'" + nickname + "'의 " + disease + " 경과 체크 시간입니다.\n지금 바로 식물을 살펴보고 상태를 선택해주세요. ✅");
        return templates.get(new Random().nextInt(templates.size()));
    }

    @Operation(summary = "[테스트] Best Friend 알림 강제 발송", description = "생애주기(기념일) 알림 스케줄러를 즉시 실행합니다.")
    @PostMapping("/send-best-friend")
    public ResponseEntity<String> sendBestFriendManual() {
        notificationScheduler.sendBestFriendNotifications();
        return ResponseEntity.ok("Best Friend(기념일) 알림 로직이 실행되었습니다.");
    }

}