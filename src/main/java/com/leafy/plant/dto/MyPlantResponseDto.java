package com.leafy.plant.dto;

import com.leafy.plant.domain.MyPlant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Builder
@Getter
@Setter
public class MyPlantResponseDto {
    private Long myPlantId;
    private String nickname;
    private String imageUrl;
    private LocalDate adoptionDate; //등록한 날짜
    private String plantSpeciesName;  //등록한 식물 종 학명 (예: 고무나무)

    // MyPlant 엔티티를 DTO로 변환하는 정적 메소드
    public static MyPlantResponseDto from(MyPlant myPlant) {
        return MyPlantResponseDto.builder()
                .myPlantId(myPlant.getPlantId())
                .nickname(myPlant.getNickname())
                .imageUrl(myPlant.getImageUrl())
                .adoptionDate(myPlant.getAdoptionDate())
                .plantSpeciesName(myPlant.getPlantSpecies().getKoreanName()) // PlantSpecies에서 이름 가져오기
                .build();
    }
}


