package com.leafy.plant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.plant.dto.PlantDataDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantDataCache {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    private static final String S3_KEY = "plants/final_plants.json";

    private final Map<Long, PlantDataDto> plantMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        try {
            log.info("Attempting to load plant data from S3...");
            try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(S3_KEY)
                    .build())) {

                List<PlantDataDto> dtoList = objectMapper.readValue(inputStream, new TypeReference<>() {});
                plantMap.putAll(dtoList.stream()
                        .collect(Collectors.toMap(PlantDataDto::getId, Function.identity())));
                log.info("Successfully loaded and cached {} plant species from S3.", plantMap.size());
            }
        } catch (Exception s3Exception) {
            log.warn("S3-WARN: Failed to load plant data from S3. Reason: {}. Attempting to load from local fallback.", s3Exception.getMessage());
            try {
                log.info("Attempting to load plant data from local fallback file: /data/final_plants.json");
                InputStream inputStream = new TypeReference<>() {}.getClass().getResourceAsStream("/data/final_plants.json");
                if (inputStream == null) {
                    throw new RuntimeException("Fallback resource not found: /data/final_plants.json");
                }
                List<PlantDataDto> dtoList = objectMapper.readValue(inputStream, new TypeReference<>() {});
                plantMap.putAll(dtoList.stream()
                        .collect(Collectors.toMap(PlantDataDto::getId, Function.identity())));
                log.info("Successfully loaded and cached {} plant species from local fallback.", plantMap.size());
            } catch (Exception fallbackException) {
                log.error("FATAL: Failed to load plant data from both S3 and local fallback. Recommendation system will be unavailable.", fallbackException);
            }
        }
    }

    public PlantDataDto getPlantById(Long id) {
        return plantMap.get(id);
    }

    public Collection<PlantDataDto> getAllPlants() {
        return plantMap.values();
    }
}
