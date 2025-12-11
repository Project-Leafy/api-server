package com.leafy.plant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PlantDataDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("koreanName")
    private String koreanName;

    @JsonProperty("scientificName")
    private String scientificName;

    @JsonProperty("imageUrl")
    private String imageUrl;

    @JsonProperty("description")
    private String description;

    @JsonProperty("flowerFruitInfo")
    private String flowerFruitInfo;

    @JsonProperty("lightLux")
    private String lightLux;

    @JsonProperty("sunlightTip")
    private String sunlightTip;

    @JsonProperty("temp")
    private String temp;

    @JsonProperty("tempTip")
    private String tempTip;

    @JsonProperty("humidity")
    private String humidity;

    @JsonProperty("humidityTip")
    private String humidityTip;

    @JsonProperty("soil")
    private String soil;

    @JsonProperty("waterSpring")
    private int waterSpring;

    @JsonProperty("waterSummer")
    private int waterSummer;

    @JsonProperty("waterAutumn")
    private int waterAutumn;

    @JsonProperty("waterWinter")
    private int waterWinter;

    @JsonProperty("waterTip")
    private String waterTip;

    @JsonProperty("fertilizerCycleDays")
    private int fertilizerCycleDays;

    @JsonProperty("fertilizerTip")
    private String fertilizerTip;

    @JsonProperty("repottingCycleYears")
    private int repottingCycleYears;

    @JsonProperty("repottingTip")
    private String repottingTip;

    @JsonProperty("growthSpeed")
    private String growthSpeed;

    @JsonProperty("displayDifficulty")
    private String displayDifficulty;

    @JsonProperty("isToxic")
    private boolean isToxic;

    @JsonProperty("toxicityInfo")
    private String toxicityInfo;

    @JsonProperty("airPurificationKeywords")
    private String airPurificationKeywords;

    @JsonProperty("airPurificationInfo")
    private String airPurificationInfo;

    @JsonProperty("checkPointList")
    private List<String> checkPointList;

    @JsonProperty("keywordTags")
    private List<String> keywordTags;
}