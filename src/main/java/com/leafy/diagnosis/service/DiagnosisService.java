package com.leafy.diagnosis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.diagnosis.dto.DiagnosisResponseDto;
import com.leafy.diagnosis.dto.PlantIdRequestDto;
import com.leafy.diagnosis.dto.PlantIdResponseDto;
import com.leafy.diagnosis.repository.DiagnosisHistoryRepository;
import com.leafy.global.storage.S3UploadService;
import com.leafy.global.type.DiagnosisFeedbackStep;
import com.leafy.plant.domain.MyPlant;
import com.leafy.global.type.PlantStatus;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class DiagnosisService {

    private final WebClient plantIdWebClient;
    private final S3UploadService s3UploadService;
    private final DiagnosisHistoryRepository diagnosisHistoryRepository;
    private final MyPlantRepository myPlantRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public DiagnosisService(@Qualifier("plantIdWebClient") WebClient plantIdWebClient,
                            S3UploadService s3UploadService,
                            DiagnosisHistoryRepository diagnosisHistoryRepository,
                            MyPlantRepository myPlantRepository,
                            UserRepository userRepository,
                            ObjectMapper objectMapper) {
        this.plantIdWebClient = plantIdWebClient;
        this.s3UploadService = s3UploadService;
        this.diagnosisHistoryRepository = diagnosisHistoryRepository;
        this.myPlantRepository = myPlantRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 식물 건강 진단 요청 및 저장
     */
    public DiagnosisResponseDto diagnosePlant(Long myPlantId, MultipartFile imageFile, Double lat, Double lon) throws IOException {

        // 1. 사용자 및 식물 소유권 검증
        String principalName = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(principalName)
                .orElseThrow(() -> new RuntimeException("User not found"));

        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new RuntimeException("Plant not found"));

        if (!myPlant.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new RuntimeException("Not authorized access to this plant");
        }

        // 2. S3 업로드
        String s3ImageUrl = s3UploadService.upload(imageFile, "diagnosis");
        log.info("Diagnosis Image Uploaded: {}", s3ImageUrl);

        // 3. Base64 변환
        String base64Data = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String base64Image = "data:" + imageFile.getContentType() + ";base64," + base64Data;

        // 4. 외부 API 요청
        PlantIdRequestDto requestBody = new PlantIdRequestDto(
                Collections.singletonList(base64Image),
                lat,
                lon,
                true,
                "all"
        );

        PlantIdResponseDto apiResponse = plantIdWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/health_assessment")
                        .queryParam("details", "local_name,description,url,treatment,classification,common_names,cause")
                        .queryParam("language", "ko")
                        .build())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(PlantIdResponseDto.class)
                .block();

        // 5. 결과 저장, 상태 변경 및 DTO 반환 (병합된 로직)
        if (apiResponse != null && apiResponse.result() != null) {
            DiagnosisHistory savedHistory = saveDiagnosisHistory(myPlant, s3ImageUrl, apiResponse);

            if (savedHistory != null) {
                // 진단이 성공적으로 저장되었으므로 식물 상태를 SICK으로 변경
                myPlant.updateStatus(PlantStatus.SICK);
                // 저장된 엔티티를 DTO로 변환해서 컨트롤러에게 반환
                return DiagnosisResponseDto.from(savedHistory);
            }
        }

        throw new RuntimeException("식물 진단에 실패했거나 결과를 저장하지 못했습니다.");
    }

    /**
     * 진단 결과 DB 저장 로직
     */
    private DiagnosisHistory saveDiagnosisHistory(MyPlant myPlant, String imageUrl, PlantIdResponseDto response) {
        try {
            PlantIdResponseDto.Result result = response.result();

            // 1. 질병 정보 추출 (Suggestions 리스트의 첫 번째 항목)
            PlantIdResponseDto.DiseaseSuggestion topDisease = null;
            if (result.disease() != null && result.disease().suggestions() != null && !result.disease().suggestions().isEmpty()) {
                topDisease = result.disease().suggestions().get(0);
            }

            // 2. 질병명 및 확률 매핑
            String diseaseName = (topDisease != null) ? topDisease.name() : "Healthy";

            // DTO 필드명: diseaseProbability (BigDecimal)
            BigDecimal diseaseProb = (topDisease != null && topDisease.diseaseProbability() != null)
                    ? topDisease.diseaseProbability() : BigDecimal.ZERO;

            // 3. 치료법 JSON 변환
            String solutionJson = null;
            if (topDisease != null && topDisease.details() != null && topDisease.details().treatment() != null) {
                solutionJson = objectMapper.writeValueAsString(topDisease.details().treatment());
            }

            // 4. 건강 여부 및 확률 매핑
            // DTO 필드명: healthProbability (BigDecimal)
            boolean isHealthyVal = (result.isHealthy() != null) && result.isHealthy().binary();

            BigDecimal healthProb = (result.isHealthy() != null && result.isHealthy().healthProbability() != null)
                    ? result.isHealthy().healthProbability() : BigDecimal.ZERO;

            // 5. 식물 여부 확률 매핑
            // DTO 필드명: isPlantProbability (BigDecimal)
            BigDecimal isPlantProb = (result.isPlant() != null && result.isPlant().isPlantProbability() != null)
                    ? result.isPlant().isPlantProbability() : BigDecimal.ZERO;

            // 6. 엔티티 빌드 및 저장 (✅ 이 코드가 올바르다)
            DiagnosisHistory history = DiagnosisHistory.builder()
                    .myPlant(myPlant)
                    .diagnosisDatetime(LocalDateTime.now())
                    .requestImageUrl(imageUrl) // 매개변수로 받은 imageUrl 사용
                    .apiAccessToken(response.accessToken())
                    .isPlantProbability(isPlantProb)
                    .isHealthy(isHealthyVal)
                    .healthProbability(healthProb)
                    .diseaseName(diseaseName)
                    .diseaseProbability(diseaseProb)
                    .solutionDetail(solutionJson)
                    // D+2, D+5 날짜 정보는 여기서 추가되어야 한다 이다.
                    .tipDate(LocalDate.now().plusDays(2))
                    .checkDate(LocalDate.now().plusDays(5))
                    .feedbackStep(DiagnosisFeedbackStep.NONE) // @Builder.Default 설정되어 있으나 명시적으로 지정
                    .build();

            return diagnosisHistoryRepository.save(history);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse treatment solution to JSON", e);
            return null;
        }

//        Boolean isHealthy = (result.isHealthy() != null) ? result.isHealthy().binary() : true;
//        BigDecimal healthProbability = (result.isHealthy() != null) ? result.isHealthy().healthProbability() : null;
//        BigDecimal isPlantProbability = (result.isPlant() != null) ? result.isPlant().isPlantProbability() : BigDecimal.ZERO;
//
//        LocalDate today = LocalDate.now();
//
//        // 엔티티 생성 및 저장
//        DiagnosisHistory history = DiagnosisHistory.builder()
//                .myPlant(myPlant)
//                .diagnosisDatetime(LocalDateTime.now())
//                .requestImageUrl(s3ImageUrl)
//                .apiAccessToken(response.accessToken())
//                .isPlantProbability(isPlantProbability)
//                .isHealthy(isHealthy)
//                .healthProbability(healthProbability)
//                .diseaseName(diseaseName)
//                .diseaseProbability(diseaseProbability)
//                .solutionDetail(solutionDetail)
//                .tipDate(today.plusDays(2))   // D+2
//                .checkDate(today.plusDays(5)) // D+5
//                .feedbackStep(DiagnosisFeedbackStep.NONE)
//                .build();
//
//        diagnosisHistoryRepository.save(history);
    }

    /**
     * 식물별 진단 이력 조회
     */
    @Transactional(readOnly = true)
    public List<DiagnosisResponseDto> findAllByMyPlantId(Long myPlantId) {
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new RuntimeException("MyPlant not found"));

        return diagnosisHistoryRepository.findAllByMyPlantOrderByDiagnosisDatetimeDesc(myPlant)
                .stream()
                .map(DiagnosisResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 진단 상세 조회
     */
    @Transactional(readOnly = true)
    public DiagnosisResponseDto findById(Long diagnosisId) {
        DiagnosisHistory history = diagnosisHistoryRepository.findById(diagnosisId)
                .orElseThrow(() -> new RuntimeException("History not found"));
        return DiagnosisResponseDto.from(history);
    }
}