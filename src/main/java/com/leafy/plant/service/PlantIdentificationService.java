package com.leafy.plant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.diagnosis.dto.PlantIdRequestDto;
import com.leafy.diagnosis.dto.PlantIdResponseDto;
import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.global.storage.S3UploadService;
import com.leafy.global.type.LightLevel;
import com.leafy.global.type.WaterFrequency;
import com.leafy.plant.domain.PlantSpecies;
import com.leafy.plant.dto.PlantDataDto;
import com.leafy.plant.dto.PlantIdentificationResponseDto;
import com.leafy.plant.repository.PlantSpeciesRepository;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class PlantIdentificationService {

    private final WebClient plantIdWebClient;
    private final S3UploadService s3UploadService;
    private final PlantSpeciesRepository plantSpeciesRepository;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;
    private final PlantDataCache plantDataCache;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    private static final String S3_KEY = "plants/final_plants.json";


    public PlantIdentificationService(@Qualifier("plantIdWebClient") WebClient plantIdWebClient, S3UploadService s3UploadService, PlantSpeciesRepository plantSpeciesRepository, UserRepository userRepository, OpenAiService openAiService, PlantDataCache plantDataCache, S3Client s3Client, ObjectMapper objectMapper) {
        this.plantIdWebClient = plantIdWebClient;
        this.s3UploadService = s3UploadService;
        this.plantSpeciesRepository = plantSpeciesRepository;
        this.userRepository = userRepository;
        this.openAiService = openAiService;
        this.plantDataCache = plantDataCache;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
    }

    public PlantIdentificationResponseDto identifyAndPrepareRegistration(
            MultipartFile imageFile, Double lat, Double lon) throws IOException {

        String principalName = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(principalName)
                .orElseThrow(() -> new EntityNotFoundException("Authenticated User not found"));

        currentUser.updateLocation(lat, lon);

        String s3ImageUrl = s3UploadService.upload(imageFile, "identification");
        log.info("Image uploaded to S3: {}", s3ImageUrl);

        String base64Data = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String base64Image = "data:" + imageFile.getContentType() + ";base64," + base64Data;

        PlantIdResponseDto response = requestPlantIdentification(base64Image, lat, lon);

        if (response.result() == null || response.result().classification() == null || response.result().classification().suggestions().isEmpty()) {
            log.error("Plant ID API Error or no suggestions: Status={}, Input={}", response.status(), response.input());
            throw new RuntimeException("Plant identification API returned no results.");
        }

        PlantIdResponseDto.Suggestion topSuggestion = response.result().classification().suggestions().get(0);

        List<PlantIdentificationResponseDto.Suggestion> suggestionList = response.result().classification().suggestions().stream()
                .map(s -> {
                    String commonNameForSuggestion = s.name();
                    if (s.details() != null && s.details().commonNames() != null && !s.details().commonNames().isEmpty()) {
                        commonNameForSuggestion = s.details().commonNames().get(0);
                    }
                    String descriptionForSuggestion = (s.details() != null && s.details().description() != null)
                            ? s.details().description().value()
                            : "제공된 상세 설명이 없습니다.";
                    return PlantIdentificationResponseDto.Suggestion.builder()
                            .name(s.name())
                            .scientificName(s.name())
                            .probability(s.probability())
                            .imageUrl(s.details() != null ? s.details().url() : null)
                            .commonName(commonNameForSuggestion)
                            .description(descriptionForSuggestion)
                            .build();
                })
                .toList();

        String scientificName = topSuggestion.name();
        String commonName = suggestionList.get(0).commonName();

        PlantSpecies species = findOrCreatePlantSpecies(topSuggestion);

        return PlantIdentificationResponseDto.builder()
                .imageUrl(s3ImageUrl)
                .scientificName(scientificName)
                .commonName(commonName)
                .speciesId(species.getSpeciesId())
                .userId(currentUser.getUserId())
                .probability(topSuggestion.probability())
                .suggestions(suggestionList)
                .build();
    }

    private PlantIdResponseDto requestPlantIdentification(String imageData, Double lat, Double lon) {
        PlantIdRequestDto requestBody = new PlantIdRequestDto(List.of(imageData), lat, lon, "all");
        return plantIdWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/identification")
                        .queryParam("details", "common_names,url,description,taxonomy,rank")
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
            log.info("[DB] 기존 식물 '{}' 정보를 사용합니다.", scientificName);
            return existingSpecies.get();
        }

        log.info("[신규 식물] '{}'에 대한 정보가 DB에 없습니다. OpenAI 및 S3 업데이트를 시작합니다.", scientificName);
        String koreanName = (suggestion.details() != null && suggestion.details().commonNames() != null && !suggestion.details().commonNames().isEmpty())
                ? suggestion.details().commonNames().get(0) : scientificName;

        try {
            // 1. OpenAI에서 관리 정보 조회
            PlantDataDto newPlantData = openAiService.getCareInfoForNewPlant(koreanName, scientificName).block();
            if (newPlantData == null) throw new RuntimeException("OpenAI returned no data.");

            // 2. S3에서 기존 JSON 파일 읽기
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(S3_KEY).build());
            List<PlantDataDto> allPlants = objectMapper.readValue(s3Object, new TypeReference<>() {});

            // 3. 새 ID 할당 및 리스트에 추가
            long maxId = allPlants.stream().mapToLong(PlantDataDto::getId).max().orElse(0L);
            newPlantData.setId(maxId + 1);
            allPlants.add(newPlantData);
            
            log.info("[S3] 신규 식물 '{}'(ID:{}) 추가. 총 {}개의 데이터.", newPlantData.getKoreanName(), newPlantData.getId(), allPlants.size());

            // 4. 수정된 리스트를 S3에 덮어쓰기
            String updatedJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(allPlants);
            s3Client.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(S3_KEY).contentType("application/json").build(),
                RequestBody.fromString(updatedJson, StandardCharsets.UTF_8)
            );
            log.info("[S3] '{}' 파일 업데이트 완료.", S3_KEY);
            
            // 5. 현재 실행중인 서버의 캐시 업데이트
            plantDataCache.addPlant(newPlantData);

            // 6. DB에 PlantSpecies 저장
            return saveNewPlantSpecies(newPlantData, suggestion);

        } catch (Exception e) {
            log.error("[신규 식물] OpenAI 또는 S3 처리 중 심각한 오류 발생. 기본 정보로 식물을 생성합니다. 오류: {}", e.getMessage());
            // 실패 시, 기본 정보로라도 PlantSpecies를 생성하여 등록 흐름을 유지
            return createDefaultPlantSpecies(suggestion);
        }
    }

    private PlantSpecies saveNewPlantSpecies(PlantDataDto dto, PlantIdResponseDto.Suggestion suggestion) {
        PlantSpecies newSpecies = PlantSpecies.builder()
                .scientificName(dto.getScientificName())
                .koreanName(dto.getKoreanName())
                .managementTipDetail(dto.getDescription())
                .optimalTempCelsius(dto.getTemp())
                .toxicityInfo(dto.getToxicityInfo())
                .isPetFriendly(!dto.isToxic())
                .wateringFrequency(WaterFrequency.NORMAL) // 이 부분은 DTO의 정수 값으로 변환 로직 추가 가능
                .sunlightLevel(LightLevel.MEDIUM) // 이 부분도 변환 로직 추가 가능
                .isVerifiedByAdmin(false)
                .build();
        return plantSpeciesRepository.save(newSpecies);
    }
    
    private PlantSpecies createDefaultPlantSpecies(PlantIdResponseDto.Suggestion suggestion) {
        String scientificName = suggestion.name();
        String koreanName = (suggestion.details() != null && suggestion.details().commonNames() != null && !suggestion.details().commonNames().isEmpty())
                ? suggestion.details().commonNames().get(0) : scientificName;
        
        PlantSpecies newSpecies = PlantSpecies.builder()
                .scientificName(scientificName)
                .koreanName(koreanName)
                .managementTipDetail("상세 정보가 없습니다.")
                .optimalTempCelsius("정보 없음")
                .toxicityInfo("정보 없음")
                .isPetFriendly(true) // 기본값은 안전으로
                .wateringFrequency(WaterFrequency.NORMAL)
                .sunlightLevel(LightLevel.MEDIUM)
                .isVerifiedByAdmin(false)
                .build();
        return plantSpeciesRepository.save(newSpecies);
    }
}