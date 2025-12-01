package com.leafy.schedule.service;

import com.leafy.global.type.WaterFrequency;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final MyPlantRepository myPlantRepository;
    private final UserRepository userRepository; // [추가됨]

    // --- [1] 캘린더용: 내 전체 스케줄 조회 ---
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getMySchedules() { // ← userId 매개변수 제거
        // SecurityContextHolder로 현재 사용자 조회
        String principalName = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(principalName)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long userId = currentUser.getUserId();

        return scheduleRepository.findAllByUserId(userId).stream()
                .map(ScheduleResponse::new)
                .collect(Collectors.toList());
    }

    // --- [2] 캘린더용: 일정 수동 추가 ---
    public Long addSchedule(ScheduleRequest request) { // ← userId 매개변수 제거
        // SecurityContextHolder로 현재 사용자 조회
        String principalName = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(principalName)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long userId = currentUser.getUserId();

        // 1. 식물 찾기
        MyPlant myPlant = myPlantRepository.findById(request.getPlantId())
                .orElseThrow(() -> new IllegalArgumentException("식물을 찾을 수 없습니다."));

        // 2. 본인 식물인지 확인 (보안)
        if (!myPlant.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 식물에만 일정을 추가할 수 있습니다.");
        }

        // 3. 스케줄 생성 및 저장
        Schedule schedule = Schedule.builder()
                .myPlant(myPlant)
                .scheduleType(request.getScheduleType())
                .nextDueDate(request.getNextDueDate())
                .frequencyDays(null)
                .notificationStatus("PENDING")
                .build();

        return scheduleRepository.save(schedule).getScheduleId();
    }

    // --- [3] (기존 기능 유지) 식물 등록 시 자동 스케줄 생성 ---
    public void createInitialSchedule(MyPlant myPlant) {
        WaterFrequency frequencyEnum = myPlant.getPlantSpecies().getWateringFrequency();
        int waterDays = convertFrequencyToDays(frequencyEnum);
        saveSchedule(myPlant, "WATER", waterDays);

        saveSchedule(myPlant, "REPOT", 365);
        saveSchedule(myPlant, "FERTILIZE", 30);
    }

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
            case RARE -> 14;
        };
    }
}