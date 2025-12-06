package com.leafy.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    // --- ▼▼▼ [수정] 이 부분을 추가하세요 ▼▼▼ ---
    @JsonProperty("recurrence_pattern") // (2) 프론트에서 보낸 snake_case를 매핑
    private String recurrencePattern;   // ex) "NONE", "DAILY", "WEEKLY", "MONTHLY"
}