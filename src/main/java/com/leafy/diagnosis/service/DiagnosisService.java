package com.leafy.diagnosis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.diagnosis.dto.PlantIdRequestDto;
import com.leafy.diagnosis.dto.PlantIdResponseDto;
import com.leafy.diagnosis.repository.DiagnosisHistoryRepository;
import com.leafy.diagnosis.dto.DiagnosisResponseDto;
import com.leafy.global.storage.S3UploadService;
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.repository.MyPlantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64; // Base64 import 추가
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DiagnosisService {

    @Value("${PLANT_ID_API_KEY}")
    private String plantIdApiKey;
    private final WebClient plantIdWebClient;
    private final S3UploadService s3UploadService;
    private final DiagnosisHistoryRepository diagnosisHistoryRepository;
    private final MyPlantRepository myPlantRepository;
    private final ObjectMapper objectMapper;

    /**
     * 식물 건강 진단 (S3 URL 대신 Base64 데이터 전송으로 변경)
     */
    public PlantIdResponseDto diagnosePlant(Long myPlantId, MultipartFile imageFile, Double lat, Double lon) throws IOException {

        // 1. MyPlant 조회
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new EntityNotFoundException("MyPlant not found: " + myPlantId));

        // 2. S3 업로드 (DB 저장 및 이력 관리용)
        String s3ImageUrl = s3UploadService.upload(imageFile, "diagnosis");

        // 3. [수정됨] Plant.id API 전송용 Base64 변환
        // URL 접근 권한 문제를 피하기 위해 파일 자체를 인코딩해서 보냅니다.
        String base64Data = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String base64Image = "data:" + imageFile.getContentType() + ";base64," + base64Data;

        // 4. 요청 DTO 생성 (Base64 + health="all")
        PlantIdRequestDto requestBody = new PlantIdRequestDto(
                List.of(base64Image), // Base64 문자열 전송
                lat,
                lon,
                true,  // similar_images
                "all"  // health check
        );

        // 5. Plant.id API 호출
        PlantIdResponseDto response = plantIdWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/identification")
                        .queryParam("language", "ko")
                        // details 파라미터 제거 (API 기본값 사용)
                        .build())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(PlantIdResponseDto.class)
                .block();

        // 6. 결과 저장
        if (response != null && response.result() != null) {
            try {
                saveDiagnosisHistory(myPlant, s3ImageUrl, response);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to save diagnosis history.", e);
            }
        }

        return response;
    }

    private void saveDiagnosisHistory(MyPlant myPlant, String s3ImageUrl, PlantIdResponseDto response) throws JsonProcessingException {
        PlantIdResponseDto.Result result = response.result();

        // 안전하게 데이터 추출
        PlantIdResponseDto.Suggestion topSuggestion = null;
        if (result.classification() != null && result.classification().suggestions() != null) {
            topSuggestion = result.classification().suggestions().stream().findFirst().orElse(null);
        }

        PlantIdResponseDto.DiseaseSuggestion topDisease = null;
        if (result.disease() != null && result.disease().suggestions() != null && !result.disease().suggestions().isEmpty()) {
            topDisease = result.disease().suggestions().get(0);
        }

        // 값 할당
        String diseaseName = (topDisease != null) ? topDisease.name() : null;
        BigDecimal diseaseProbability = (topDisease != null) ? topDisease.diseaseProbability() : null;

        String solutionDetail = null;
        if (topDisease != null && topDisease.details() != null && topDisease.details().treatment() != null) {
            solutionDetail = objectMapper.writeValueAsString(topDisease.details().treatment());
        }

        Boolean isHealthy = (result.isHealthy() != null) ? result.isHealthy().binary() : true;
        BigDecimal healthProbability = (result.isHealthy() != null) ? result.isHealthy().healthProbability() : null;
        BigDecimal isPlantProbability = (result.isPlant() != null) ? result.isPlant().isPlantProbability() : BigDecimal.ZERO;

        // 엔티티 생성 및 저장
        DiagnosisHistory history = DiagnosisHistory.builder()
                .myPlant(myPlant)
                .diagnosisDatetime(LocalDateTime.now())
                .requestImageUrl(s3ImageUrl)
                .apiAccessToken(response.accessToken())
                .isPlantProbability(isPlantProbability)
                .isHealthy(isHealthy)
                .healthProbability(healthProbability)
                .diseaseName(diseaseName)
                .diseaseProbability(diseaseProbability)
                .solutionDetail(solutionDetail)
                .build();

        diagnosisHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<DiagnosisResponseDto> findAllByMyPlantId(Long myPlantId) {
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new EntityNotFoundException("MyPlant not found: " + myPlantId));

        return diagnosisHistoryRepository.findAllByMyPlantOrderByDiagnosisDatetimeDesc(myPlant)
                .stream()
                .map(DiagnosisResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DiagnosisResponseDto findById(Long diagnosisId) {
        DiagnosisHistory history = diagnosisHistoryRepository.findById(diagnosisId)
                .orElseThrow(() -> new EntityNotFoundException("Diagnosis History not found: " + diagnosisId));

        return DiagnosisResponseDto.from(history);
    }
}