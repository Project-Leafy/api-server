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
import com.leafy.notification.service.WeatherService;

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
    private final WeatherService weatherService;

    // [스마트] 아침 관리 알림 (물주기, 분갈이, 비료)
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @Transactional
    public void sendMorningNotifications() {
        LocalDate today = LocalDate.now();
        log.info("[Scheduler] {} 스마트 아침 알림 발송 시작...", today);

        List<Schedule> schedules = scheduleRepository.findAllByNextDueDateAndNotificationStatus(today, "PENDING");

        if (schedules.isEmpty()) {
            log.info("[Scheduler] 오늘 발송할 알림이 없습니다.");
            return;
        }

        int successCount = 0;

        for (Schedule schedule : schedules) {
            User user = schedule.getMyPlant().getUser();
            String plantNickname = schedule.getMyPlant().getNickname();
            String type = schedule.getScheduleType();

            // 1. 날씨 확인 (사용자 위치 기반)
            boolean isRaining = false;
            if (user.getLatitude() != null && user.getLongitude() != null) {
                isRaining = weatherService.willItRainToday(user.getLatitude(), user.getLongitude());
            }

            // 2. 스마트 메시지 생성 (날씨 + 일정 타입 고려)
            String message = createSmartMessage(type, plantNickname, isRaining);

            // 3. 발송
            if (kakaoMessageService.sendSelfMessage(user, message)) {
                schedule.changeNotificationStatus("SENT");
                saveNotificationHistory(user, schedule, message, type); // 오버로딩된 메서드 사용
                successCount++;
            }
        }
        log.info("[Scheduler] 알림 발송 완료. 성공: {}건", successCount);
    }
    // 🎲 [New] 스마트 알림 메시지 생성기
    private String createSmartMessage(String type, String nickname, boolean isRaining) {
        String header = isRaining ? "☔ [Leafy 비오는 날 알림]\n\n" : "🌞 [Leafy 맑은 날 알림]\n\n";
        String body = getRandomSmartTemplate(type, nickname, isRaining);
        return header + body;
    }

    private String getRandomSmartTemplate(String type, String nickname, boolean isRaining) {
        List<String> templates = new ArrayList<>();

        switch (type) {
            case "WATERING" -> {
                if (isRaining) { // 비 오는 날 물주기 멘트 (습도 고려)
                    templates.add("비가 와서 습도가 높아요. '" + nickname + "' 물주기를 하루 미루는 건 어떨까요?");
                    templates.add("오늘같이 비 오는 날엔 과습 주의! 💧 흙이 바짝 말랐는지 꼭 확인하세요.");
                    templates.add("창밖엔 비가 주룩주룩. '" + nickname + "'도 공기 중의 수분을 즐기고 있을 거예요.");
                    templates.add("습한 날씨엔 물주기에 신중해야 해요. 겉흙뿐만 아니라 속흙까지 확인해 주세요!");
                    templates.add("비 오는 날의 물주기는 보약보다 독이 될 수도 있어요. 상태를 보고 결정하세요. 🤔");
                    templates.add("축축한 날씨네요. '" + nickname + "' 통풍에 더 신경 써주세요! 🌬️");
                    templates.add("혹시 베란다 문이 닫혀있나요? 물주기보다 환기가 더 중요한 날입니다.");
                    templates.add("하늘이 물을 주는 날이네요. 실내 식물들은 조금 더 건조하게 관리해도 좋아요.");
                    templates.add("습도가 빵빵해요! '" + nickname + "'에게 물 대신 사랑의 눈빛만 주는 건 어때요? 👀");
                    templates.add("비 소식이 있어요. 오늘 물주기는 건너뛰고 내일 맑을 때 주는 것도 방법이에요!");
                } else { // 맑은 날 물주기 멘트
                    templates.add("햇살 좋은 오늘, '" + nickname + "'에게 시원한 물 한 잔 어떠세요? 💧");
                    templates.add("오늘은 물 주는 날! 화분 밑으로 물이 나올 때까지 흠뻑 주세요.");
                    templates.add("'" + nickname + "'가 목말라하고 있어요. 흙 상태 확인 후 물을 챙겨주세요.");
                    templates.add("맑은 날엔 광합성도 활발해요! 물과 햇빛으로 에너지를 채워주세요. ☀️");
                    templates.add("똑똑! '" + nickname + "' 물주기 알람입니다. 잊지 말고 챙겨주실 거죠?");
                    templates.add("식물도 물 마실 시간! 잎에 분무도 같이 해주면 더 좋아할 거예요. 🌿");
                    templates.add("오늘의 할 일: '" + nickname + "' 물 주기 완료하고 상쾌한 하루 시작하기!");
                    templates.add("흙이 마르기 딱 좋은 날씨네요. 수분 보충 타임입니다!");
                    templates.add("싱그러운 아침, '" + nickname + "'와 함께 물 주기 명상 어떠세요? 🧘");
                    templates.add("미루지 마세요! 오늘 물을 줘야 '" + nickname + "'가 쑥쑥 자라요.");
                }
            }
            case "REPOTTING" -> {
                if (isRaining) {
                    templates.add("비가 오네요. 분갈이하기엔 흙이 잘 안 마를 수 있어요. 맑은 날을 기다려볼까요?");
                    templates.add("습한 날 분갈이는 뿌리에 무리를 줄 수 있어요. 오늘은 화분 정리만 해보세요. 🧹");
                    templates.add("'" + nickname + "'의 새 집 이사, 비 그치고 화창한 날에 하는 걸 추천해요!");
                    templates.add("오늘 같은 날은 분갈이 계획만 세우고, 실행은 다음으로 미루는 게 좋아요.");
                    templates.add("분갈이 대신 잎의 먼지를 닦아주며 교감하는 시간을 가져보세요. ✨");
                } else {
                    templates.add("화창한 오늘이 바로 D-Day! '" + nickname + "'에게 더 넓은 집을 선물하세요. 🏠");
                    templates.add("뿌리가 답답해 보여요. 오늘 분갈이해주면 폭풍 성장할 거예요!");
                    templates.add("새 흙과 새 화분으로 기분 전환! '" + nickname + "' 분갈이 도전?");
                    templates.add("날씨가 너무 좋아요. 베란다에서 흙 만지며 힐링하는 분갈이 타임 어때요?");
                    templates.add("'" + nickname + "'가 쑥쑥 자랐네요. 이제 더 큰 화분으로 이사 갈 시간입니다!");
                    templates.add("분갈이 후 물 듬뿍 주고 통풍 잘 되는 곳에 두기, 잊지 마세요!");
                    templates.add("오늘 분갈이하면 뿌리 활착이 아주 잘 될 거예요. 화이팅! 💪");
                }
            }
            case "FERTILIZING" -> {
                // 비료는 날씨 영향을 덜 받지만, 흐린 날보단 맑은 날(광합성 활발)이 흡수에 좋음
                if (isRaining) {
                    templates.add("비 오는 날엔 영양제 흡수가 더딜 수 있어요. 날이 개면 주는 게 더 좋아요! 🌤️");
                    templates.add("'" + nickname + "'에게 영양제를 줄 시기지만, 오늘은 해가 없어서 조금 아쉽네요.");
                    templates.add("비료보다는 환기가 더 필요한 날씨! 영양제는 맑은 날 선물해 주세요.");
                    templates.add("오늘 영양제를 주신다면 농도를 조금 묽게 해서 주는 건 어떨까요?");
                } else {
                    templates.add("햇빛 가득한 오늘, 영양제까지 더해지면 '" + nickname + "'는 천하무적! 💪");
                    templates.add("보약 한 첩 지어왔어요~ 💊 '" + nickname + "'에게 영양제를 줄 시간입니다.");
                    templates.add("쑥쑥 크는 게 보여요. 성장을 돕기 위해 비료를 챙겨주세요.");
                    templates.add("광합성 뿜뿜하는 오늘! 비료 흡수율도 최고일 거예요. 🌱");
                    templates.add("'" + nickname + "' 잎의 색이 더 진해지도록, 맛있는 영양분을 공급해 주세요!");
                    templates.add("식물도 밥심! 알비료나 액체비료로 에너지를 충전해 주세요.");
                }
            }
            default -> templates.add("'" + nickname + "' 관리 알림이 있습니다. 앱에서 확인해 주세요!");
        }

        // 템플릿이 비어있지 않으면 랜덤 반환, 비어있으면 기본값
        if (templates.isEmpty()) return "'" + nickname + "' 관리 알림입니다.";
        return templates.get(new Random().nextInt(templates.size()));
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