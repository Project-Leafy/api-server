package com.leafy.schedule.service;

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
        // 1. 도감에서 물주기 주기 코드 가져오기 (예: "DAYS_7", "NORMAL" 등)
        String cycleCode = myPlant.getPlantSpecies().getWateringCycleCode();

        // 2. 코드를 일수(Integer)로 변환
        int frequency = convertCodeToDays(cycleCode);

        // 3. 스케줄 엔티티 생성
        Schedule schedule = Schedule.builder()
                .myPlant(myPlant)
                .scheduleType("WATERING") // 물주기
                .frequencyDays(frequency)
                .nextDueDate(LocalDate.now().plusDays(frequency)) // 오늘 + 주기 = 다음 예정일
                .notificationStatus("ACTIVE")
                .build();

        // 4. 저장
        scheduleRepository.save(schedule);
    }

    // 💡 헬퍼 메서드: 코드를 숫자로 변환 (규칙은 팀끼리 정하기 나름)
    private int convertCodeToDays(String code) {
        // 예시 규칙 (실제 DB 데이터에 맞춰 수정 필요)
        if (code == null) return 7; // 기본값 7일

        return switch (code.toUpperCase()) {
            case "DAYS_3", "FREQUENT" -> 3;
            case "DAYS_7", "NORMAL" -> 7;
            case "DAYS_14", "RARE" -> 14;
            case "DAYS_30" -> 30;
            default -> 7; // 모르면 일단 7일
        };
    }
}