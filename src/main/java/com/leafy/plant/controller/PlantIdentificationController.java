package com.leafy.plant.controller;

import com.leafy.plant.dto.PlantIdentificationResponseDto;
import com.leafy.plant.service.PlantIdentificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/plants")
public class PlantIdentificationController {

    private final PlantIdentificationService plantIdentificationService;

    /**
     * 새로운 식물 이미지 파일을 업로드 받아 plant.id로 식별하고, 등록을 위한 정보를 반환합니다.
     */
    @Operation(summary = "식물 이미지 식별 및 등록 준비",
            description = "사용자가 업로드한 이미지를 S3에 저장하고, plant.id API로 식물 종을 식별하여 등록에 필요한 정보를 반환합니다.",
            requestBody = @RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = PlantIdentificationRequestSchema.class)
            )))
    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()") // 인증된 사용자만 호출 가능
    public ResponseEntity<PlantIdentificationResponseDto> identifyNewPlant(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lon", required = false) Double lon) throws IOException {

        if (imageFile.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // 서비스 호출: S3 업로드 -> API 식별 -> PlantSpecies 등록/조회
        PlantIdentificationResponseDto response = plantIdentificationService.identifyAndPrepareRegistration(imageFile, lat, lon);

        return ResponseEntity.ok(response);
    }
}

// Swagger의 파일 업로드 폼 생성을 돕는 더미 클래스
class PlantIdentificationRequestSchema {
    @Schema(description = "진단할 이미지 파일", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    public MultipartFile image;

    @Schema(description = "위도", example = "37.5665")
    public Double lat;

    @Schema(description = "경도", example = "126.9780")
    public Double lon;
}
