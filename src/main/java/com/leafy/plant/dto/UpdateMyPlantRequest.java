package com.leafy.plant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class UpdateMyPlantRequest {

    // 닉네임 수정 시
    private String nickname;

    // 입양일 수정 시 (Snake Case 매핑 필수!)
    @JsonProperty("adoption_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    private LocalDate adoptionDate;
}