package com.leafy.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class ScheduleRequest {

    private Long plantId;        // JSON의 plant_id 가 여기로 자동 매핑됨

    private String scheduleType; // JSON의 schedule_type 이 여기로 자동 매핑됨

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    private LocalDate nextDueDate; // JSON의 next_due_date 가 여기로 자동 매핑됨
}