// 경로: api-server/src/main/java/com/leafy/diagnosis/controller/DiagnosisController.java
package com.leafy.diagnosis.controller;

import com.leafy.diagnosis.dto.PlantIdResponseDto;
import com.leafy.diagnosis.service.DiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/diagnosis")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    /**
     * 식물 식별 요청 (S3 저장 및 JSON+URL 방식)
     * @param myPlantId 사용자의 식물 ID
     * @param imageFile 사용자가 업로드한 이미지
     * @param lat 위도 (선택)
     * @param lon 경도 (선택)
     * @return plant.id API의 응답 DTO
     * @throws IOException
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()") // 인증된 사용자만 호출 가능
    public ResponseEntity<PlantIdResponseDto> createDiagnosis(
            @RequestParam("myPlantId") Long myPlantId,
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lon", required = false) Double lon) throws IOException {

        if (imageFile.isEmpty()) {
            return ResponseEntity.badRequest().build(); // 400 Bad Request
        }

        // 서비스를 호출하여 S3 업로드 및 식별 수행
        PlantIdResponseDto response = diagnosisService.diagnosePlant(myPlantId, imageFile, lat, lon);

        return ResponseEntity.ok(response);
    }
}