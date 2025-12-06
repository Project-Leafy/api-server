package com.leafy.schedule.service;

import com.leafy.global.type.WaterFrequency;
import com.leafy.notification.service.KakaoMessageService; // [1] 임포트 추가
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.schedule.domain.Schedule;
import com.leafy.schedule.dto.ScheduleRequest;
import com.leafy.schedule.dto.ScheduleResponse;
import com.leafy.schedule.repository.ScheduleRepository;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final MyPlantRepository myPlantRepository;
    private final UserRepository userRepository;

    // [2] 알림 서비스를 사용하기 위해 추가
    private final KakaoMessageService kakaoMessageService;

    // --- [1] 캘린더용: 내 전체 스케줄 조회 ---
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getMySchedules() {
        String principalName = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(principalName)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long userId = currentUser.getUserId();

        return scheduleRepository.findAllByUserId(userId).stream()
                .map(ScheduleResponse::new)
                .collect(Collectors.toList());
    }

    // --- [2] 캘린더용: 일정 수동 추가 + 알림 발송 ---
    public Long addSchedule(ScheduleRequest request) {
        // 1. 사용자 조회
        String principalName = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(principalName)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long userId = currentUser.getUserId();

        // 2. 식물 찾기
        MyPlant myPlant = myPlantRepository.findById(request.getPlantId())
                .orElseThrow(() -> new IllegalArgumentException("식물을 찾을 수 없습니다."));

        // 3. 본인 식물인지 확인
        if (!myPlant.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 식물에만 일정을 추가할 수 있습니다.");
        }

        Integer frequency = convertRecurrenceToDays(request.getRecurrencePattern());

        // 4. 스케줄 생성 및 저장
        Schedule schedule = Schedule.builder()
                .myPlant(myPlant)
                .scheduleType(request.getScheduleType())
                .nextDueDate(request.getNextDueDate())
                .frequencyDays(frequency) // (2) 변환된 값을 저장
                .notificationStatus("PENDING")
                .build();

        Schedule savedSchedule = scheduleRepository.save(schedule);

        // [3] ✨ 알림 발송 로직 추가됨
        try {
            String typeKorean = convertTypeToKorean(request.getScheduleType());
            String message = String.format("✅ [Leafy 일정 등록]\n\n'%s'의 '%s' 일정이 등록되었습니다!\n\n📅 날짜: %s",
                    myPlant.getNickname(), typeKorean, request.getNextDueDate());

            kakaoMessageService.sendSelfMessage(currentUser, message);
        } catch (Exception e) {
            // 알림 발송 실패가 일정 저장 자체를 막으면 안 되므로 로그만 찍고 넘어감
            System.err.println("알림 발송 실패: " + e.getMessage());
        }

        return savedSchedule.getScheduleId();
    }

    private Integer convertRecurrenceToDays(String recurrencePattern) {
        if (recurrencePattern == null || Objects.equals(recurrencePattern.toUpperCase(), "NONE")) {
            return null; // 반복 안함은 null
        }
        return switch (recurrencePattern.toUpperCase()) {
            case "DAILY" -> 1;
            case "WEEKLY" -> 7;
            case "MONTHLY" -> 30; // 단순하게 30일로 계산
            default -> null;
        };
    }

    // --- [3] (기존 기능 유지) 식물 등록 시 자동 스케줄 생성 ---
//    public void createInitialSchedule(MyPlant myPlant) {
//        WaterFrequency frequencyEnum = myPlant.getPlantSpecies().getWateringFrequency();
//        int waterDays = convertFrequencyToDays(frequencyEnum);
//        saveSchedule(myPlant, "WATER", waterDays);
//
//        saveSchedule(myPlant, "REPOT", 365);
//        saveSchedule(myPlant, "FERTILIZE", 30);
//    }

    private void saveSchedule(MyPlant myPlant, String type, int frequencyDays) {
        Schedule schedule = Schedule.builder()
                .myPlant(myPlant)
                .scheduleType(type)
                .frequencyDays(frequencyDays)
                .nextDueDate(LocalDate.now().plusDays(frequencyDays))
                .notificationStatus("PENDING")
                .build();

        scheduleRepository.save(schedule);
    }

    private int convertFrequencyToDays(WaterFrequency frequency) {
        if (frequency == null) return 7;
        return switch (frequency) {
            case FREQUENT -> 3;
            case NORMAL -> 7;
            case INFREQUENT -> 14;
        };
    }

    // [4] 타입을 한글로 예쁘게 바꿔주는 헬퍼 메서드
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
}