package com.leafy.plant.service;

import com.leafy.diagnosis.dto.PlantIdRequestDto;
import com.leafy.diagnosis.dto.PlantIdResponseDto;
import com.leafy.global.storage.S3UploadService;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.WaterFrequency;
import com.leafy.plant.domain.PlantSpecies;
import com.leafy.plant.dto.PlantIdentificationResponseDto;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.plant.repository.PlantSpeciesRepository;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlantIdentificationService {

    @Value("${PLANT_ID_API_KEY}")
    private String plantIdApiKey;
    private final WebClient plantIdWebClient;
    private final S3UploadService s3UploadService;
    private final PlantSpeciesRepository plantSpeciesRepository;
    private final MyPlantRepository myPlantRepository;
    private final UserRepository userRepository;

    public PlantIdentificationResponseDto identifyAndPrepareRegistration(
            MultipartFile imageFile, Double lat, Double lon) throws IOException {

        // 1. 사용자 확인
        String principalName = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(principalName)
                .orElseThrow(() -> new EntityNotFoundException("Authenticated User not found"));

        // 위치 정보가 있으면 사용자 정보 갱신 (Smart 알림용)
        // lat, lon이 null이면 updateLocation 내부에서 무시됨
        currentUser.updateLocation(lat, lon);

        // 2. S3 업로드 (DB 저장용)
        String s3ImageUrl = s3UploadService.upload(imageFile, "identification");
        log.info("Image uploaded to S3: {}", s3ImageUrl);

        // 3. [수정됨] Plant.id 전송용 Base64 변환 (Data URI Scheme 적용!)
        // 예: "data:image/jpeg;base64,/9j/4AAQSkZJRg..." 형식으로 만들어야 API가 인식함
        String base64Data = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String base64Image = "data:" + imageFile.getContentType() + ";base64," + base64Data;

        // 4. 식별 요청
        PlantIdResponseDto response = requestPlantIdentification(base64Image, lat, lon);

        // 5. 결과 처리
        if (response.result() == null || response.result().classification() == null) {
            // API가 결과를 주지 않았을 때의 로그
            log.error("Plant ID API Error: Status={}, Input={}", response.status(), response.input());
            throw new RuntimeException("Plant identification API returned no results.");
        }

        PlantIdResponseDto.Suggestion topSuggestion = response.result().classification().suggestions().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No plant suggestions found."));

        String scientificName = topSuggestion.name();
        String commonName = "알 수 없는 식물";
        if (topSuggestion.details() != null &&
                topSuggestion.details().commonNames() != null &&
                !topSuggestion.details().commonNames().isEmpty()) {
            commonName = topSuggestion.details().commonNames().get(0);
        }

        // 6. DB 등록/조회
        PlantSpecies species = findOrCreatePlantSpecies(topSuggestion);

        Double probability = topSuggestion.probability();

        return PlantIdentificationResponseDto.builder()
                .imageUrl(s3ImageUrl)
                .scientificName(scientificName)
                .commonName(commonName)
                .speciesId(species.getSpeciesId())
                .userId(currentUser.getUserId())
                .probability(probability)
                .build();
    }

    private PlantIdResponseDto requestPlantIdentification(String imageData, Double lat, Double lon) {
        PlantIdRequestDto requestBody = new PlantIdRequestDto(
                List.of(imageData),
                lat,
                lon,
                true,
                "all"
        );

        return plantIdWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/identification")
                        .queryParam("details", "common_names,url,description,taxonomy,rank")  //수정
                        .queryParam("language", "ko")
                        .build())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(PlantIdResponseDto.class)
                .block();
    }

    private PlantSpecies findOrCreatePlantSpecies(PlantIdResponseDto.Suggestion suggestion) {
        String scientificName = suggestion.name();
        Optional<PlantSpecies> existingSpecies = plantSpeciesRepository.findByScientificName(scientificName);

        if (existingSpecies.isPresent()) {
            return existingSpecies.get();
        }

        String koreanName = scientificName;
        if (suggestion.details() != null &&
                suggestion.details().commonNames() != null &&
                !suggestion.details().commonNames().isEmpty()) {
            koreanName = suggestion.details().commonNames().get(0);
        }

        // 👇 [수정 2] 설명(Description) 꺼내는 코드 추가 (여기부터)
        String description = "상세 정보가 없습니다.";
        if (suggestion.details() != null && suggestion.details().description() != null) {
            description = suggestion.details().description().value();
        }

        String optimalTemp = "정보 없음";
        String toxicityInfo = "정보 없음";
        // 👆 (여기까지 추가)

        // 👇 [수정 3] 빌더(Builder)에 managementTipDetail 넣기
        PlantSpecies newSpecies = PlantSpecies.builder()
                .scientificName(scientificName)
                .koreanName(koreanName)
                .managementTipDetail(description) // 👈 이 줄을 꼭 추가해야 한다!
                .optimalTempCelsius(optimalTemp)  // 👈 (선택) 기본값 저장
                .toxicityInfo(toxicityInfo)       // 👈 (선택) 기본값 저장
                .wateringFrequency(WaterFrequency.NORMAL)
                .sunlightLevel(LightLevel.MEDIUM)
                .isVerifiedByAdmin(false)
                .build();

        return plantSpeciesRepository.save(newSpecies);
    }
}