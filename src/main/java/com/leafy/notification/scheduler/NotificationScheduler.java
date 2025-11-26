package com.leafy.notification.scheduler;

import com.leafy.notification.domain.Notification;
import com.leafy.notification.repository.NotificationRepository;
import com.leafy.notification.service.KakaoMessageService;
import com.leafy.schedule.domain.Schedule;
import com.leafy.schedule.repository.ScheduleRepository;
import com.leafy.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final ScheduleRepository scheduleRepository;
    private final KakaoMessageService kakaoMessageService;
    private final NotificationRepository notificationRepository;

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

    // 멘트 생성 로직 분리
    private String createMessageByType(String type, String nickname) {
        String header = "🌱 [Leafy 알림]\n\n";

        return switch (type) {
            case "WATERING" -> String.format(header + "오늘은 '%s' 물 주는 날이에요! 💧\n흙 상태를 꼼꼼히 확인하고 물을 주세요.", nickname);

            case "REPOTTING" -> String.format(header + "'%s'와(과) 함께한 지 꽤 되었네요! 🪴\n화분이 작아 보인다면 분갈이를 고려해 보세요.", nickname);

            case "FERTILIZING" -> String.format(header + "'%s'에게 영양제를 줄 시기입니다. 💊\n쑥쑥 자라도록 영양을 챙겨주세요!", nickname);

            default -> String.format(header + "'%s' 관리 알림이 있습니다.", nickname);
        };
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
}