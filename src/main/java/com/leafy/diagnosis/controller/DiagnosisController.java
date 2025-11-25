package com.leafy.diagnosis.controller;

import com.leafy.diagnosis.dto.DiagnosisResponseDto;
import com.leafy.diagnosis.dto.PlantIdResponseDto;
import com.leafy.diagnosis.service.DiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "Diagnosis", description = "식물 AI 진단 및 기록 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/diagnosis")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    @Operation(summary = "식물 건강 진단 요청", description = "식물 사진을 업로드하여 AI에게 병해충 진단을 요청하고 결과를 저장합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlantIdResponseDto> createDiagnosis(
            @Parameter(description = "진단할 내 식물 ID") @RequestParam("myPlantId") Long myPlantId,
            @Parameter(description = "식물 사진 파일") @RequestParam("image") MultipartFile imageFile,
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lon", required = false) Double lon) throws IOException {

        if (imageFile.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        PlantIdResponseDto response = diagnosisService.diagnosePlant(myPlantId, imageFile, lat, lon);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "식물별 진단 기록 목록 조회", description = "특정 식물의 과거 진단 이력을 최신순으로 조회합니다.")
    @GetMapping("/plants/{myPlantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DiagnosisResponseDto>> getDiagnosisHistoryList(
            @PathVariable Long myPlantId) {

        List<DiagnosisResponseDto> historyList = diagnosisService.findAllByMyPlantId(myPlantId);
        return ResponseEntity.ok(historyList);
    }

    @Operation(summary = "진단 기록 상세 조회", description = "특정 진단 기록의 상세 내용(병명, 확률, 솔루션 등)을 조회합니다.")
    @GetMapping("/{diagnosisId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DiagnosisResponseDto> getDiagnosisDetail(
            @PathVariable Long diagnosisId) {

        DiagnosisResponseDto detail = diagnosisService.findById(diagnosisId);
        return ResponseEntity.ok(detail);
    }
}