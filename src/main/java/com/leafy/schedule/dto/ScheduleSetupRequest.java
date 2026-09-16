package com.leafy.schedule.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ScheduleSetupRequest {
    private Long plantId; // 어떤 식물에 대한 설정인지 식별하기 위함
    private String scheduleType; // "WATERING", "REPOTTING", "FERTILIZING"
    private String mode;         // "AUTO", "MANUAL"
    private Integer frequencyDays; // MANUAL 모드일 때 사용
}
