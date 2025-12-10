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
        log.info("Loading plant data cache from S3...");
        try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(S3_KEY)
                .build())) {
            List<PlantDataDto> dtoList = objectMapper.readValue(inputStream, new TypeReference<>() {});

            plantMap.putAll(dtoList.stream()
                    .collect(Collectors.toMap(PlantDataDto::getId, Function.identity())));

            log.info("Successfully loaded and cached {} plant species from S3.", plantMap.size());
        } catch (Exception e) {
            log.error("FATAL: Failed to load initial plant data from S3. Recommendation system will not work.", e);
            // In a real application, you might want to prevent the application from starting if this fails.
        }
    }

    public PlantDataDto getPlantById(Long id) {
        return plantMap.get(id);
    }

    public Collection<PlantDataDto> getAllPlants() {
        return plantMap.values();
    }
}
