package com.leafy.schedule.service;

import com.leafy.global.type.WaterFrequency; // Enum import 필수
import com.leafy.plant.domain.MyPlant;
import com.leafy.schedule.domain.Schedule;
import com.leafy.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    /**
     * 식물 등록 시 3종 관리 스케줄(물주기, 분갈이, 비료) 자동 생성
     */
    public void createInitialSchedule(MyPlant myPlant) {
        // 1. 물주기 스케줄 (기존 로직)
        WaterFrequency frequencyEnum = myPlant.getPlantSpecies().getWateringFrequency();
        int waterDays = convertFrequencyToDays(frequencyEnum);
        saveSchedule(myPlant, "WATERING", waterDays);

        // 2. 분갈이 스케줄 (기본 1년)
        // 추후 식물 크기나 성장 속도에 따라 조정 가능
        saveSchedule(myPlant, "REPOTTING", 365);

        // 3. 비료/영양제 스케줄 (기본 30일)
        // 겨울철(휴면기) 등은 추후 날짜 계산 로직에서 제외 가능
        saveSchedule(myPlant, "FERTILIZING", 30);
    }

    // 스케줄 저장 헬퍼 메서드
    private void saveSchedule(MyPlant myPlant, String type, int frequencyDays) {
        Schedule schedule = Schedule.builder()
                .myPlant(myPlant)
                .scheduleType(type) // WATERING, REPOTTING, FERTILIZING
                .frequencyDays(frequencyDays)
                .nextDueDate(LocalDate.now().plusDays(frequencyDays)) // 오늘 + 주기 = 예정일
                .notificationStatus("PENDING")
                .build();

        scheduleRepository.save(schedule);
    }

    // [수정] 파라미터를 String -> WaterFrequency Enum으로 변경
    private int convertFrequencyToDays(WaterFrequency frequency) {
        if (frequency == null) return 7; // 기본값

        return switch (frequency) {
            case FREQUENT -> 3;  // 자주 (3일)
            case NORMAL -> 7;    // 보통 (7일)
            case RARE -> 14;     // 가끔 (14일)
        };
    }
}