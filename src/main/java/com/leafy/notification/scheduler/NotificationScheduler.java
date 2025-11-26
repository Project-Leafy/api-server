package com.leafy.notification.scheduler;

import com.leafy.notification.domain.Notification;
import com.leafy.notification.repository.NotificationRepository;
import com.leafy.notification.service.KakaoMessageService;
import com.leafy.plant.domain.MyPlant;
import com.leafy.schedule.domain.Schedule;
import com.leafy.schedule.repository.ScheduleRepository;
import com.leafy.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.global.type.DiagnosisFeedbackStep;
import com.leafy.diagnosis.repository.DiagnosisHistoryRepository;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final ScheduleRepository scheduleRepository;
    private final KakaoMessageService kakaoMessageService;
    private final NotificationRepository notificationRepository;
    private final DiagnosisHistoryRepository diagnosisHistoryRepository;

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @Transactional
    public void sendMorningNotifications() {
        LocalDate today = LocalDate.now();
        log.info("[Scheduler] {} 아침 알림 발송 시작...", today);

        // 오늘 예정된 모든 'PENDING' 스케줄 조회 (타입 구분 없이 다 가져옴)
        List<Schedule> schedules = scheduleRepository.findAllByNextDueDateAndNotificationStatus(today, "PENDING");

        if (schedules.isEmpty()) {
            log.info("[Scheduler] 오늘 발송할 알림이 없습니다.");
            return;
        }

        int successCount = 0;

        for (Schedule schedule : schedules) {
            User user = schedule.getMyPlant().getUser();
            String plantNickname = schedule.getMyPlant().getNickname();
            String type = schedule.getScheduleType(); // 일정 타입 (WATERING, etc.)

            // 1. 타입별 메시지 생성
            String message = createMessageByType(type, plantNickname);

            // 2. 카카오톡 전송
            boolean isSent = kakaoMessageService.sendSelfMessage(user, message);

            if (isSent) {
                schedule.changeNotificationStatus("SENT");
                saveNotificationHistory(user, schedule, message, type);
                successCount++;
            } else {
                log.warn("알림 발송 실패. ScheduleID: {}, User: {}", schedule.getScheduleId(), user.getEmail());
            }
        }
        log.info("[Scheduler] 알림 발송 완료. 대상: {}건, 성공: {}건", schedules.size(), successCount);
    }

    /**
     * [AI Doctor] 진단 후속 케어 알림 (D+2, D+5)
     * 매일 오전 9시에 실행
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void sendDiagnosisFollowUpNotifications() {
        LocalDate today = LocalDate.now();
        log.info("[AI Doctor] 진단 후속 알림 체크 시작: {}", today);

        // 1. D+2: 관리 팁 발송 (대상: 팁 날짜가 오늘이고, 아직 단계가 NONE인 경우)
        List<DiagnosisHistory> tipTargets = diagnosisHistoryRepository
                .findAllByTipDateAndFeedbackStep(today, DiagnosisFeedbackStep.NONE);

        for (DiagnosisHistory history : tipTargets) {
            sendTipNotification(history);
        }

        // 2. D+5: 상태 확인 요청 (대상: 체크 날짜가 오늘이고, 팁은 보낸 상태인 경우)
        List<DiagnosisHistory> checkTargets = diagnosisHistoryRepository
                .findAllByCheckDateAndFeedbackStep(today, DiagnosisFeedbackStep.TIP_SENT);

        for (DiagnosisHistory history : checkTargets) {
            sendCheckRequestNotification(history);
        }
    }

    // 기본 3종 알림 발송 로직
    private String createMessageByType(String type, String nickname) {
        String header = "🌱 [Leafy 알림]\n\n";

        return switch (type) {
            case "WATERING" -> String.format(header + "오늘은 '%s' 물 주는 날이에요! 💧\n흙 상태를 꼼꼼히 확인하고 물을 주세요.", nickname);

            case "REPOTTING" -> String.format(header + "'%s'와(과) 함께한 지 꽤 되었네요! 🪴\n화분이 작아 보인다면 분갈이를 고려해 보세요.", nickname);

            case "FERTILIZING" -> String.format(header + "'%s'에게 영양제를 줄 시기입니다. 💊\n쑥쑥 자라도록 영양을 챙겨주세요!", nickname);

            default -> String.format(header + "'%s' 관리 알림이 있습니다.", nickname);
        };
    }

    // 진단 2틀뒤 팁 알림 발송 로직
    // Todo 나중에 관리 팁을 DB에 저장해두면 이를 가져와야함
    private void sendTipNotification(DiagnosisHistory history) {
        User user = history.getMyPlant().getUser();
        String plantName = history.getMyPlant().getNickname();
        String diseaseName = history.getDiseaseName();

        String message = String.format("""
                💊 [Leafy 닥터] 관리 팁 도착!
                
                '%s'의 %s 치료는 시작하셨나요?
                약제를 뿌린 후에는 '환기'가 필수입니다! 🌬️
                
                오늘 창가 쪽으로 자리를 옮겨주는 건 어떨까요?""",
                plantName, diseaseName != null ? diseaseName : "증상");

        if (kakaoMessageService.sendSelfMessage(user, message)) {
            history.updateStep(DiagnosisFeedbackStep.TIP_SENT); // 상태 변경
            saveNotificationHistory(user, history.getMyPlant(), message, "DIAGNOSIS_TIP");
        }
    }

    // 진단 5일 후 식물의 경과 확인
    private void sendCheckRequestNotification(DiagnosisHistory history) {
        User user = history.getMyPlant().getUser();
        String plantName = history.getMyPlant().getNickname();

        String message = String.format("""
                🔍 [Leafy 닥터] 상태 확인
                
                '%s'의 치료를 시작한 지 5일이 지났어요.
                상태가 좀 나아졌나요?
                
                아래 버튼을 눌러 Leafy에게 알려주세요!""", plantName);

        if (kakaoMessageService.sendSelfMessage(user, message)) {
            history.updateStep(DiagnosisFeedbackStep.CHECK_REQUESTED); // 상태 변경
            saveNotificationHistory(user, history.getMyPlant(), message, "DIAGNOSIS_CHECK");
        }
    }

    private void saveNotificationHistory(User user, Schedule schedule, String message, String type) {
        Notification notification = Notification.builder()
                .user(user)
                .myPlant(schedule.getMyPlant())
                .notificationType(type)
                .message(message)
                .relatedSchedule(schedule)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    //AI Doctor용 저장 (Schedule 없음, MyPlant 직접 받음)
    private void saveNotificationHistory(User user, MyPlant myPlant, String message, String type) {
        Notification notification = Notification.builder()
                .user(user)
                .myPlant(myPlant)
                .notificationType(type)
                .message(message)
                .relatedSchedule(null) // 스케줄 아님
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }
}