package com.leafy.plant.dto;

import com.leafy.global.type.PlantStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter; // Setter를 추가하여 JSON 역직렬화 시 값을 설정할 수 있도록 합니다.

@Getter
@Setter // Request DTO는 주로 Setter가 필요합니다.
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlantStatusRequest {
    private PlantStatus status;
}
