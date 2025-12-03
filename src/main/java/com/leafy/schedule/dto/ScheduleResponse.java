package com.leafy.schedule.dto;

import com.leafy.schedule.domain.Schedule;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import java.time.LocalDate;

@Getter
// ✅ 응답 보낼 때도 Camel Case로 변환해서 프론트엔드 JS가 잘 알아먹게 함
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class ScheduleResponse {
    private Long scheduleId;
    private Long plantId;
    private String plantNickname;
    private String scheduleType;
    private LocalDate nextDueDate;

    public ScheduleResponse(Schedule schedule) {
        this.scheduleId = schedule.getScheduleId();
        // NullPointerException 방지를 위한 안전장치 추가 (권장)
        if (schedule.getMyPlant() != null) {
            this.plantId = schedule.getMyPlant().getPlantId();
            this.plantNickname = schedule.getMyPlant().getNickname();
        }
        this.scheduleType = schedule.getScheduleType();
        this.nextDueDate = schedule.getNextDueDate();
    }
}