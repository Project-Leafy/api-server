package com.leafy.plant.controller;

import com.leafy.plant.dto.CareInfoResponse;
import com.leafy.plant.service.MyPlantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Plant Species", description = "식물 종 공통 정보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plant-species")
public class PlantSpeciesController {

    private final MyPlantService myPlantService;

    @Operation(summary = "AI 추천 관리 정보 조회", description = "특정 식물 종(speciesId)에 대한 추천 관리 주기(물주기, 비료, 분갈이) 정보를 조회합니다.")
    @GetMapping("/{speciesId}/care-info")
    public ResponseEntity<CareInfoResponse> getCareInfo(@PathVariable Long speciesId) {
        CareInfoResponse careInfo = myPlantService.getCareInfo(speciesId);
        return ResponseEntity.ok(careInfo);
    }
}
