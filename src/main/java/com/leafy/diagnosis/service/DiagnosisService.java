package com.leafy.diagnosis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.diagnosis.dto.PlantIdRequestDto;
import com.leafy.diagnosis.dto.PlantIdResponseDto;
import com.leafy.diagnosis.repository.DiagnosisHistoryRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DiagnosisService {

    @Value("${PLANT_ID_API_KEY}") // .env 파일의 값을 주입
    private String plantIdApiKey;
    private final WebClient plantIdWebClient; // WebClientConfig에서 생성된 Bean 주입
    private final S3UploadService s3UploadService; // S3 업로드 서비스 주입
    private final DiagnosisHistoryRepository diagnosisHistoryRepository;
    private final MyPlantRepository myPlantRepository; // MyPlant 조회를 위해 주입
    private final ObjectMapper objectMapper; // Treatment 객체를 JSON 문자열로 변환하기 위해 주입

    /**
     * S3에 이미지를 업로드하고, 그 URL로 plant.id 식별 요청 (JSON 방식)
     */
    public PlantIdResponseDto diagnosePlant(Long myPlantId, MultipartFile imageFile, Double lat, Double lon) throws IOException {

        // 1. MyPlant 엔티티 조회
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new EntityNotFoundException("MyPlant not found: " + myPlantId));

        // 2. S3에 이미지 업로드 (S3UploadService 사용)
        String s3ImageUrl = s3UploadService.upload(imageFile, "diagnosis"); // "diagnosis" 폴더에 저장

        List<String> requestedDetails = List.of(
                "common_names",
                "url",
                "description",
                "diseaseDetails" // 질병 진단 결과에 필요한 항목
        );
        // 3. plant.id API 요청 DTO 생성 (Base64가 아닌 S3 URL 전송)
        PlantIdRequestDto requestBody = new PlantIdRequestDto(
                List.of(s3ImageUrl),
                lat,
                lon,
                "all", // 'all'로 설정하여 건강 진단 요청
                plantIdApiKey // ⭐️ API Key 전달
                //requestedDetails // ⭐️ Details 목록 전달
        );

        // 4. plant.id API 호출 (JSON 방식)
        PlantIdResponseDto response = plantIdWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/identification")
                        .queryParam("language", "ko")
                        // ⭐️ List를 comma-separated String으로 변환하여 쿼리 파라미터로 전송
                        .queryParam("details", String.join(",", requestedDetails))
                        .build())
                .bodyValue(requestBody) // DTO를 JSON 본문으로 전송
                .retrieve()
                .bodyToMono(PlantIdResponseDto.class)
                .block();

        // 5. API 응답 이력 저장
        if (response != null && "COMPLETED".equalsIgnoreCase(response.status())) {
            // JsonProcessingException 발생 가능성이 있으므로 try-catch로 감쌉니다.
            try {
                saveDiagnosisHistory(myPlant, s3ImageUrl, response);
            } catch (JsonProcessingException e) {
                // JSON 변환 실패 시 예외 처리 (로그 기록 등)
                // 현재는 런타임 예외로 감싸서 던집니다.
                throw new RuntimeException("Failed to save diagnosis history due to JSON processing error.", e);
            }
        }

        return response;
    }

    /**
     * 식별 이력을 DB에 저장하는 헬퍼 메소드 (수정된 부분)
     */
    private void saveDiagnosisHistory(MyPlant myPlant, String s3ImageUrl, PlantIdResponseDto response) throws JsonProcessingException {

        PlantIdResponseDto.Result result = response.result();
        PlantIdResponseDto.Suggestion topSuggestion = result.classification().suggestions().stream()
                .findFirst().orElse(null);

        // DiseaseSuggestion은 null 체크와 함께 안전하게 가져옵니다.
        PlantIdResponseDto.DiseaseSuggestion topDisease = (result.disease() != null && result.disease().suggestions() != null && !result.disease().suggestions().isEmpty()) ?
                result.disease().suggestions().get(0) : null;

        // DTO에서 정보 추출
        String plantName = (topSuggestion != null) ? topSuggestion.name() : null;
        String plantCommonName = (topSuggestion != null && topSuggestion.details() != null && topSuggestion.details().commonNames() != null && !topSuggestion.details().commonNames().isEmpty()) ?
                topSuggestion.details().commonNames().get(0) : null;

        BigDecimal isPlantProbability = result.isPlant().isPlantProbability();
        Boolean isHealthy = (result.isHealthy() != null) ? result.isHealthy().binary() : true; // 건강 정보가 없으면 true 간주
        BigDecimal healthProbability = (result.isHealthy() != null) ? result.isHealthy().healthProbability() : null;

        String diseaseName = null;
        BigDecimal diseaseProbability = null;
        String solutionDetail = null;

        if(topDisease != null) {
            diseaseName = topDisease.name();
            diseaseProbability = topDisease.diseaseProbability();
            // Treatment 객체를 JSON 문자열로 변환하여 solutionDetail에 저장
            if (topDisease.details() != null && topDisease.details().treatment() != null) {
                solutionDetail = objectMapper.writeValueAsString(topDisease.details().treatment());
            }
        }

        // DiagnosisHistory 엔티티 생성
        DiagnosisHistory history = DiagnosisHistory.builder()
                .myPlant(myPlant)
                .diagnosisDatetime(LocalDateTime.now())
                .requestImageUrl(s3ImageUrl) // S3 URL 저장
                .apiAccessToken(response.accessToken())
                .isPlantProbability(isPlantProbability)
                .isHealthy(isHealthy)
                .healthProbability(healthProbability)
                .diseaseName(diseaseName)
                .diseaseProbability(diseaseProbability)
                .solutionDetail(solutionDetail)
                // feedback 관련 필드는 초기값 null
                .build();

        // 4. 저장
        diagnosisHistoryRepository.save(history);
    }
}
