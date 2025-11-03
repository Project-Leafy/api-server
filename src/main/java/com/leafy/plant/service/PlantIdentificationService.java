package com.leafy.plant.service;

import com.leafy.diagnosis.dto.PlantIdRequestDto;
import com.leafy.diagnosis.dto.PlantIdResponseDto;
// ... (나머지 import 유지) ...
import com.leafy.global.storage.S3UploadService;
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.domain.PlantSpecies;
import com.leafy.plant.dto.PlantIdentificationResponseDto;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.plant.repository.PlantSpeciesRepository;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value; // import 추가
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PlantIdentificationService {

    @Value("${PLANT_ID_API_KEY}") // .env 파일의 값을 주입
    private String plantIdApiKey; // ⭐️ 필드 추가
    private final WebClient plantIdWebClient;
    private final S3UploadService s3UploadService;
    private final PlantSpeciesRepository plantSpeciesRepository;
    private final MyPlantRepository myPlantRepository;
    private final UserRepository userRepository;

    /**
     * 새로운 식물 이미지를 식별하고, PlantSpecies를 등록/조회하며 MyPlant 등록을 위한 DTO를 반환합니다.
     */
    public PlantIdentificationResponseDto identifyAndPrepareRegistration(
            MultipartFile imageFile, Double lat, Double lon) throws IOException {

        // 1. 사용자 조회 (인증된 사용자만 접근 가능)
        String principalName = SecurityContextHolder.getContext().getAuthentication().getName();

        // ⬅️ 수정: Principal의 값을 이메일로 간주하고 findByEmail을 사용합니다.
        // 이 로직이 성공하려면, JWT 토큰 생성 시 Principal에 반드시 'email'이 담겨야 합니다.
        User currentUser = userRepository.findByEmail(principalName) // ⬅️ findByNickname 대신 findByEmail 사용
                .orElseThrow(() -> new EntityNotFoundException("Authenticated User not found: " + principalName));

        // 2. S3에 이미지 업로드 및 URL 획득
        String imageUrl = s3UploadService.upload(imageFile, "identification");

        // 3. plant.id API 호출 (식별 요청)
        PlantIdResponseDto response = requestPlantIdentification(imageUrl, lat, lon);

        // 4. 식별 결과 추출
        PlantIdResponseDto.Suggestion topSuggestion = response.result().classification().suggestions().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Plant identification failed. No suggestions found."));

        String scientificName = topSuggestion.name();
        String commonName = topSuggestion.details().commonNames() != null && !topSuggestion.details().commonNames().isEmpty() ?
                topSuggestion.details().commonNames().get(0) : "알 수 없는 식물";

        // 5. PlantSpecies 등록/조회 (DB 관리)
        PlantSpecies species = findOrCreatePlantSpecies(topSuggestion);

        // 6. MyPlant 등록을 위한 응답 DTO 구성
        return PlantIdentificationResponseDto.builder()
                .imageUrl(imageUrl)
                .scientificName(scientificName)
                .commonName(commonName)
                .speciesId(species.getSpeciesId())
                .userId(currentUser.getUserId()) // ⬅️ User 엔티티의 PK 필드명(userId)을 따른 Getter 사용
                .build();
    }

    // --- 헬퍼 메소드 ---

    private PlantIdResponseDto requestPlantIdentification(String imageUrl, Double lat, Double lon) {
        // PlantIdRequestDto 생성자 순서: (images, lat, lon, similarImages(boolean), health(String))

        // ⭐️ [해결] requestedDetails 변수 정의 코드를 추가합니다.
        List<String> requestedDetails = List.of(
                "common_names",
                "official_image_url",
                "description",
                "watering",
                "light_condition"
        );
        PlantIdRequestDto requestBody = new PlantIdRequestDto(
                List.of(imageUrl),
                lat,
                lon,
                null, // health: String 타입
                plantIdApiKey // ⭐️ DTO에 API Key만 전달
                // requestedDetails <--- 제거
        );

        return plantIdWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/identification")
                        .queryParam("language", "ko")
                        .queryParam("details", String.join(",", requestedDetails))
                        .build())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(PlantIdResponseDto.class)
                .block();
    }

    private PlantSpecies findOrCreatePlantSpecies(PlantIdResponseDto.Suggestion suggestion) {
        // 학명으로 DB에서 기존 종을 찾습니다.
        String scientificName = suggestion.name();
        Optional<PlantSpecies> existingSpecies = plantSpeciesRepository.findByScientificName(scientificName);

        if (existingSpecies.isPresent()) {
            return existingSpecies.get();
        }

        // 새로운 종이라면 DB에 등록합니다. (최소한의 정보만 사용)
        String koreanName = suggestion.details().commonNames() != null && !suggestion.details().commonNames().isEmpty() ?
                suggestion.details().commonNames().get(0) : scientificName;

        PlantSpecies newSpecies = PlantSpecies.builder()
                .scientificName(scientificName)
                .koreanName(koreanName)
                // API에서 watering/light 정보를 받아오면 여기에 매핑해야 함.
                .wateringCycleCode("NORMAL")
                .sunlightLevelCode("INDIRECT")
                .isVerifiedByAdmin(false) // API로 등록된 종은 미검증 상태로 설정
                .build();

        return plantSpeciesRepository.save(newSpecies);
    }
}
