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
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.repository.MyPlantRepository;
import java.time.temporal.ChronoUnit;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final ScheduleRepository scheduleRepository;
    private final KakaoMessageService kakaoMessageService;
    private final NotificationRepository notificationRepository;
    private final DiagnosisHistoryRepository diagnosisHistoryRepository;
    private final MyPlantRepository myPlantRepository;

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

    // 1. [D+2] 관리 팁 발송 로직 (수정됨: 랜덤 템플릿 적용)
    private void sendTipNotification(DiagnosisHistory history) {
        User user = history.getMyPlant().getUser();
        String plantName = history.getMyPlant().getNickname();
        String diseaseName = history.getDiseaseName() != null ? history.getDiseaseName() : "증상";

        // 랜덤 메시지 생성
        String messageBody = getRandomTipTemplate(plantName, diseaseName);
        String fullMessage = String.format("💊 [Leafy 닥터] 관리 팁 도착!\n\n%s", messageBody);

        if (kakaoMessageService.sendSelfMessage(user, fullMessage)) {
            history.updateStep(DiagnosisFeedbackStep.TIP_SENT); // 상태 변경
            saveNotificationHistory(user, history.getMyPlant(), fullMessage, "DIAGNOSIS_TIP");
        }
    }

    // 2. [D+5] 경과 확인 발송 로직 (수정됨: 랜덤 템플릿 적용)
    private void sendCheckRequestNotification(DiagnosisHistory history) {
        User user = history.getMyPlant().getUser();
        String plantName = history.getMyPlant().getNickname();
        String diseaseName = history.getDiseaseName() != null ? history.getDiseaseName() : "증상";

        // 랜덤 메시지 생성
        String messageBody = getRandomCheckTemplate(plantName, diseaseName);
        String fullMessage = String.format("🔍 [Leafy 닥터] 상태 확인\n\n%s\n\n👇 아래 버튼을 눌러 상태를 기록해주세요!", messageBody);

        if (kakaoMessageService.sendSelfMessage(user, fullMessage)) {
            history.updateStep(DiagnosisFeedbackStep.CHECK_REQUESTED); // 상태 변경
            saveNotificationHistory(user, history.getMyPlant(), fullMessage, "DIAGNOSIS_CHECK");
        }
    }

    // 🎲 [Helper] D+2 팁 메시지 랜덤 생성기
    private String getRandomTipTemplate(String nickname, String disease) {
        List<String> templates = new ArrayList<>();

        // Variation 1: 환기 강조
        templates.add("'" + nickname + "'의 " + disease + " 치료는 시작하셨나요?\n약제를 뿌린 후에는 '환기'가 필수입니다! 🌬️ 창문을 활짝 열어주세요.");

        // Variation 2: 격려 및 애정
        templates.add("'" + nickname + "'가 많이 아파서 속상하시죠? 🥺\n집사님의 정성 어린 치료와 따뜻한 말 한마디면 금방 나을 거예요!");

        // Variation 3: 환경 관리 (습도/햇빛)
        templates.add("병해충 퇴치 꿀팁! 🍯\n약제 살포 후 잎이 젖어있을 땐 강한 햇빛을 피하고, 바람이 잘 통하는 곳에 두세요.");

        // Variation 4: 물주기 주의
        templates.add("치료 중에는 식물도 예민해요. 💧\n'" + nickname + "'의 흙 상태를 손가락으로 꼭 확인하고 물을 주세요. 과습은 금물!");

        // Variation 5: 잎 관리 (닦아주기)
        templates.add("'" + nickname + "'의 잎을 깨끗이 닦아주셨나요? ✨\n잎에 쌓인 먼지만 제거해도 숨쉬기가 훨씬 편해진답니다.");

        Random random = new Random();
        return templates.get(random.nextInt(templates.size()));
    }

    // 🎲 [Helper] D+5 상태 확인 메시지 랜덤 생성기
    private String getRandomCheckTemplate(String nickname, String disease) {
        List<String> templates = new ArrayList<>();

        // Variation 1: 닥터 리피 페르소나
        templates.add("👨‍⚕️ 닥터 리피 회진 시간입니다.\n'" + nickname + "'의 " + disease + " 증상은 좀 가라앉았나요? 상태를 알려주세요.");

        // Variation 2: 궁금증 유발
        templates.add("치료를 시작한 지 벌써 5일이 지났네요.\n'" + nickname + "'는 다시 건강을 되찾고 있을까요? 너무 궁금해요! 👀");

        // Variation 3: 걱정과 케어
        templates.add("아직도 '" + nickname + "'가 힘들어하고 있진 않나요? 🏥\n상태가 호전되지 않았다면 재진단이 필요할 수도 있어요.");

        // Variation 4: 꾸준한 관리 독려
        templates.add("꾸준한 관찰이 식물을 살립니다! 📝\n지난번 진단 이후 '" + nickname + "'에게 어떤 변화가 있었는지 기록해주세요.");

        // Variation 5: 심플/직관
        templates.add("'" + nickname + "'의 " + disease + " 경과 체크 시간입니다.\n지금 바로 식물을 살펴보고 상태를 선택해주세요. ✅");

        Random random = new Random();
        return templates.get(random.nextInt(templates.size()));
    }

    // [Best Friend] 시간(Lifecycle) 기반 알림
    // 매일 오전 10시에 실행
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    @Transactional
    public void sendBestFriendNotifications() {
        LocalDate today = LocalDate.now();
        log.info("[Best Friend] 생애주기 알림 체크 시작: {}", today);

        // 1. 체크할 D-Day 목록 정의 (오늘 기준으로 입양일이 언제여야 하는지 역산)
        // 예: 오늘이 D+3이려면, 입양일은 '오늘 - 3일'이어야 함
        Map<LocalDate, Integer> targetDateMap = new HashMap<>();
        List<Integer> dDays = List.of(1, 3, 7, 14, 30, 100, 200, 300, 365, 730); // 기념일 목록

        for (int dDay : dDays) {
            targetDateMap.put(today.minusDays(dDay), dDay);
        }

        // 2. 해당 날짜에 입양된 식물들 한 번에 조회 (Batch Fetch)
        List<LocalDate> targetDates = new ArrayList<>(targetDateMap.keySet());
        List<MyPlant> celebratingPlants = myPlantRepository.findAllByAdoptionDateIn(targetDates);

        if (celebratingPlants.isEmpty()) {
            return;
        }

        // 3. 알림 발송
        for (MyPlant plant : celebratingPlants) {
            int dDay = targetDateMap.get(plant.getAdoptionDate()); // 며칠째인지 확인
            sendAnniversaryMessage(plant, dDay);
        }
    }

    private void sendAnniversaryMessage(MyPlant plant, int dDay) {
        User user = plant.getUser();
        String nickname = plant.getNickname();

        // 랜덤 메시지 선택
        String messageBody = getRandomTemplate(dDay, nickname);
        String fullMessage = String.format("🎉 [Leafy 베프 알림]\n\n%s", messageBody);

        // 카톡 발송 및 저장
        if (kakaoMessageService.sendSelfMessage(user, fullMessage)) {
            // 알림 내역 저장 (Notification Type: LIFECYCLE_CARE)
            saveNotificationHistory(user, plant, fullMessage, "LIFECYCLE_CARE");
        }
    }

    // 🎲 랜덤 메시지 생성기 (수정됨)
    private String getRandomTemplate(int dDay, String nickname) {
        List<String> templates = new ArrayList<>();

        if (dDay == 1) {
            templates.add("반가워요! '" + nickname + "'와(과) 오늘부터 1일! 🌱 잘 부탁드려요.");
            templates.add("설레는 첫 만남! '" + nickname + "'가 집에 온 걸 환영해요.");
            templates.add("새로운 가족이 생겼네요. '" + nickname + "'와 멋진 추억을 만들어보세요!");
        }
        else if (dDay == 3) {
            templates.add("✨ 작심삼일은 지났다! '" + nickname + "'와(과) 3일째, 시작이 좋네요.");
            templates.add("벌써 3일? '" + nickname + "'가 주인님을 아주 마음에 들어 해요. 👀");
            templates.add("3일의 고비를 넘기셨군요! 이대로 쭉 함께해요. 🌿");
        }
        else if (dDay == 7) {
            templates.add("일주일 동안 '" + nickname + "'가 잘 적응했네요! 👏");
            templates.add("벌써 일주일! '" + nickname + "'와(과) 조금 친해지셨나요?");
        }
        else if (dDay == 30) {
            templates.add("🏆 '" + nickname + "'와(과) 함께한 지 30일! 이제 우린 제법 잘 어울려요.");
            templates.add("와우! '" + nickname + "'가 온 지 벌써 한 달. 건강하게 키워주셔서 감사합니다!");
            templates.add("초보 딱지 떼셔도 되겠는데요? 한 달 동안 건강하게 키워주셔서 감사합니다. 🎉");
        }
        // 🎂 N주년 (1년, 2년...) - 100일 단위보다 먼저 체크해야 함
        else if (dDay % 365 == 0) {
            int year = dDay / 365;
            templates.add("🥇 감동의 " + year + "주년! 사계절을 " + year + "번이나 함께 견뎌낸 '" + nickname + "'와(과) 집사님, 정말 존경합니다.");
            templates.add("Happy Birthday! 🎂 '" + nickname + "'가 가족이 된 지 벌써 " + year + "년이 되었습니다.");
            templates.add("시간 참 빠르죠? '" + nickname + "'와의 " + year + "년, 변함없는 사랑에 감사드려요.");
            templates.add("이제 '" + nickname + "' 없는 일상은 상상할 수 없어요. " + year + "주년을 진심으로 축하합니다! 🎉");
            templates.add("식물 키우기 고수 인정! 👍 '" + nickname + "'와 함께한 " + year + "년의 추억을 되돌아보는 건 어떨까요?");
        }
        // 🎉 100일 단위 (100, 200, 300... 단, 365일 배수는 위에서 걸러짐)
        else if (dDay % 100 == 0) {
            // Variation 1: 축하형
            templates.add("🎂 오늘은 '" + nickname + "'가 온 지 " + dDay + "일째 되는 날! 떡이라도 돌려야겠어요. 🍡");
            templates.add("💯점 만점에 100점 집사님! '" + nickname + "'와의 " + dDay + "일을 축하합니다.");

            // Variation 2: 회상형
            templates.add("시간 참 빠르죠? '" + nickname + "'와(과) 함께한 지 벌써 " + dDay + "일이 되었어요.");
            templates.add("처음 '" + nickname + "'를 데려오던 날 기억나시나요? 벌써 " + dDay + "일이 지났답니다.");

            // Variation 3: 칭찬형
            templates.add("꾸준함이 재능이라면, 집사님은 천재! 🎓 '" + nickname + "'를 " + dDay + "일 동안 지켜주셨군요.");
            templates.add("'" + nickname + "'가 집사님을 만나서 정말 다행이에요. " + dDay + "일 동안 사랑해주셔서 감사합니다. 💚");

            // Variation 4: 애착형
            templates.add("이제 '" + nickname + "'는 집사님의 베스트 프렌드! 🌿 " + dDay + "일 기념 특식(비료) 어떠세요?");
            templates.add("우리의 우정은 영원히! ✨ '" + nickname + "'와 " + dDay + "일째 동거 중.");

            // Variation 5: 기록 독려형
            templates.add(dDay + "일 동안 얼마나 자랐을까요? 📏 오늘 '" + nickname + "'의 모습을 사진으로 남겨보세요.");
            templates.add("특별한 날엔 기록이 필수! 📸 '" + nickname + "'와의 " + dDay + "일 기념 셀카는 어떠세요?");
        }
        // 그 외 (혹시 모를 예외 처리)
        else {
            templates.add("🎉 '" + nickname + "'와(과) 함께한 지 " + dDay + "일째입니다. 오늘도 초록초록한 하루 되세요!");
        }

        // 리스트에서 랜덤으로 하나 뽑기
        Random random = new Random();
        return templates.get(random.nextInt(templates.size()));
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