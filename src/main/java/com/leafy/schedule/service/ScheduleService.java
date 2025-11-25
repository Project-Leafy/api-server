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
     * 식물 등록 시 초기 물주기 스케줄 자동 생성
     */
    public void createInitialSchedule(MyPlant myPlant) {
        // [수정] String -> Enum(WaterFrequency)으로 변경됨
        WaterFrequency frequencyEnum = myPlant.getPlantSpecies().getWateringFrequency();

        // 2. Enum을 일수(Integer)로 변환
        int frequency = convertFrequencyToDays(frequencyEnum);

        // 3. 스케줄 엔티티 생성
        Schedule schedule = Schedule.builder()
                .myPlant(myPlant)
                .scheduleType("WATERING")
                .frequencyDays(frequency)
                .nextDueDate(LocalDate.now().plusDays(frequency))
                // notificationStatus는 @Builder.Default로 설정했으므로 생략 가능(혹은 명시)
                .notificationStatus("ACTIVE")
                .build();

        // 4. 저장
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