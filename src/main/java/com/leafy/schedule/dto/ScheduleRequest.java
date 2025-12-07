package com.leafy.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class ScheduleRequest {

    // ✅ (중요) 여기에 @JsonProperty를 붙여야 "plant_id" 값을 제대로 인식한다.
    @JsonProperty("plant_id")
    private Long plantId;

    // ✅ 여기도 붙여주는 것이 안전하다.
    @JsonProperty("schedule_type")
    private String scheduleType;

    // ✅ 여기도 붙여준다.
    @JsonProperty("next_due_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    private LocalDate nextDueDate;

    // ✅ 프론트엔드에서 'frequency_days'로 보내고 있다면 이름표를 맞춰줘야 한다.
    // (만약 프론트에서 'recurrence_pattern'으로 보낸다면 값을 "recurrence_pattern"으로 변경)
    @JsonProperty("frequency_days")
    private Integer frequencyDays;
}