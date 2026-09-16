package com.leafy.notification.controller;

import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.diagnosis.repository.DiagnosisHistoryRepository;
import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.notification.domain.Notification;
import com.leafy.notification.repository.NotificationRepository;
import com.leafy.notification.scheduler.NotificationScheduler;
import com.leafy.notification.service.KakaoMessageService;
import com.leafy.notification.service.WeatherService;
import com.leafy.schedule.domain.Schedule;
import com.leafy.schedule.repository.ScheduleRepository;
import com.leafy.user.domain.User;
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.repository.MyPlantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    private final MyPlantRepository myPlantRepository;

    private final ScheduleRepository scheduleRepository;
    private final WeatherService weatherService;

    // ✨ [테스트 API] 일정 등록 완료 알림 강제 발송
    @Operation(summary = "[테스트] 일정 등록 완료 알림", description = "캘린더에 일정을 추가했을 때 발송될 '등록 완료' 알림을 테스트합니다.")
    @PostMapping("/test/schedule-create")
    public ResponseEntity<String> sendScheduleCreateNotificationTest(
            @RequestParam Long myPlantId,
            @RequestParam String scheduleType, // WATERING, REPOTTING 등
            @RequestParam String date // "2023-12-25" (표시용)
    ) {
        // 1. 식물 및 유저 정보 조회
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new EntityNotFoundException("식물을 찾을 수 없습니다. ID: " + myPlantId));

        User user = myPlant.getUser();
        String nickname = myPlant.getNickname();

        // 2. 메시지 구성
        String typeKorean = convertTypeToKorean(scheduleType); // 영어 타입을 한글로 변환
        String message = String.format("🌱 [Leafy 일정 등록]\n\n'%s'의 '%s' 일정이 등록되었습니다!\n\n📅 날짜: %s\n\n잊지 않도록 당일에 다시 알려드릴게요! 😉",
                nickname, typeKorean, date);

        // 3. 카카오톡 전송
        boolean isSent = kakaoMessageService.sendSelfMessage(user, message);

        if (isSent) {
            return ResponseEntity.ok("일정 등록 알림 발송 성공!\n내용:\n" + message);
        } else {
            return ResponseEntity.status(500).body("카카오 메시지 발송 실패");
        }
    }

    // 타입을 한글로 바꿔주는 헬퍼 메서드
    private String convertTypeToKorean(String type) {
        if (type == null) return "관리";
        return switch (type.toUpperCase()) {
            case "WATER", "WATERING" -> "물주기";
            case "REPOT", "REPOTTING" -> "분갈이";
            case "FERTILIZE", "FERTILIZING" -> "비료주기";
            case "PRUNE", "PRUNING" -> "가지치기";
            default -> "관리";
        };
    }

    @Operation(summary = "[테스트] 오늘 일정 즉시 알림 발송", description = "오늘 날짜의 물주기/분갈이/비료 알림을 바로 발송합니다.")
    @PostMapping("/send-today-schedule")
    @Transactional
    public ResponseEntity<String> sendTodayScheduleTest() {
        LocalDate today = LocalDate.now();
        List<Schedule> schedules = scheduleRepository.findAllByNextDueDateAndNotificationStatus(today, "PENDING");

        if (schedules.isEmpty()) {
            return ResponseEntity.ok("오늘("+ today +")은 보낼 일정이 없습니다.");
        }

        int successCount = 0;
        for (Schedule schedule : schedules) {
            User user = schedule.getMyPlant().getUser();
            String plantNickname = schedule.getMyPlant().getNickname();
            String type = schedule.getScheduleType();

            // 날씨 확인 로직
            boolean isRaining = false;
            if (user.getLatitude() != null && user.getLongitude() != null) {
                isRaining = weatherService.isRainingNow(user.getLatitude(), user.getLongitude());
            }

            // ✅ 누락되었던 메서드 구현 완료
            String message = createSmartMessage(type, plantNickname, isRaining);

            if (kakaoMessageService.sendSelfMessage(user, message)) {
                schedule.changeNotificationStatus("SENT");
                // ✅ 누락되었던 메서드 구현 완료
                saveNotificationHistory(user, schedule, message, "SCHEDULE_TEST");
                successCount++;
            }
        }

        return ResponseEntity.ok("오늘 일정 알림 발송 완료 ✅ (" + successCount + "건 성공)");
    }

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

    // ==========================================
    // 👇 누락되었던 헬퍼 메서드 추가됨
    // ==========================================

    private String createSmartMessage(String type, String nickname, boolean isRaining) {
        String header = isRaining ? "☔ [Leafy 비오는 날 알림]\n\n" : "🌞 [Leafy 맑은 날 알림]\n\n";
        String body = getRandomSmartTemplate(type, nickname, isRaining);
        return header + body;
    }

    private void saveNotificationHistory(User user, Schedule schedule, String message, String type) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .myPlant(schedule.getMyPlant())
                .notificationType(type)
                .message(message)
                .relatedSchedule(schedule)
                .isRead(false)
                .build());
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

    // ✨ [테스트 API] 스마트 알림(날씨/관리) 강제 발송
    @Operation(summary = "[테스트] 스마트 관리 알림 강제 발송",
            description = "특정 식물에 대해 날씨 조건(비/맑음)을 설정하여 스마트 알림을 즉시 발송합니다.")
    @PostMapping("/test/smart-notification")
    @Transactional
    public ResponseEntity<String> sendTestSmartNotification(
            @RequestParam Long myPlantId,
            @RequestParam String scheduleType, // WATERING, REPOTTING, FERTILIZING
            @RequestParam boolean isRaining // true: 비 옴, false: 맑음
    ) {
        // 1. 식물 정보 조회
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new EntityNotFoundException("식물을 찾을 수 없습니다. ID: " + myPlantId));

        User user = myPlant.getUser();
        String nickname = myPlant.getNickname();

        // 2. 스마트 메시지 생성 (랜덤 템플릿)
        String header = isRaining ? "☔ [Leafy 비오는 날 알림]\n\n" : "🌞 [Leafy 맑은 날 알림]\n\n";
        String body = getRandomSmartTemplate(scheduleType, nickname, isRaining);
        String message = header + body;

        // 3. 카카오톡 전송
        boolean isSent = kakaoMessageService.sendSelfMessage(user, message);

        // 4. 알림 내역 저장
        if (isSent) {
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .myPlant(myPlant)
                    .notificationType(scheduleType)
                    .message(message)
                    .relatedSchedule(null) // 테스트이므로 스케줄 연동 안 함
                    .isRead(false)
                    .build());
            return ResponseEntity.ok("테스트 알림 발송 성공! (" + (isRaining ? "비" : "맑음") + ")\n내용: " + message);
        } else {
            return ResponseEntity.status(500).body("카카오 메시지 발송 실패");
        }
    }

    // --- [테스트용] 스마트 템플릿 복사본 (Scheduler와 동일) ---
    private String getRandomSmartTemplate(String type, String nickname, boolean isRaining) {
        List<String> templates = new ArrayList<>();

        switch (type) {
            case "WATERING" -> {
                if (isRaining) {
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
                } else {
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

        if (templates.isEmpty()) return "'" + nickname + "' 관리 알림입니다.";
        return templates.get(new Random().nextInt(templates.size()));
    }
}