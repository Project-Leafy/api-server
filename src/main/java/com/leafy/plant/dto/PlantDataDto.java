package com.leafy.plant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter; // Lombok Setter 추가 (ObjectMapper 필요 시)

import java.util.List;

@Getter
@Setter // ObjectMapper가 필드를 채우려면 Setter가 필요할 수 있습니다.
@NoArgsConstructor
public class PlantDataDto {
    private Long id;
    private String koreanName;
    private String scientificName;
    private String imageUrl;
    private String description;
    private String flowerFruitInfo;
    private String lightLux;
    private String sunlightTip;
    private String temp;
    private String tempTip;
    private String humidity;
    private String humidityTip;
    private String soil;
    private int waterSpring;
    private int waterSummer;
    private int waterAutumn;
    private int waterWinter;
    private String waterTip;
    private int fertilizerCycleDays;
    private String fertilizerTip;
    private int repottingCycleYears;
    private String repottingTip;
    private String growthSpeed;
    private String displayDifficulty; // JSON 필드명과 일치
    private boolean isToxic;
    private String toxicityInfo;
    private String airPurificationKeywords;
    private String airPurificationInfo;
    private List<String> checkPointList;
    private List<String> keywordTags;
}
