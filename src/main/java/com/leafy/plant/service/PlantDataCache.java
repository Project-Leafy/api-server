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
            log.info("[Cache] S3에서 식물 데이터 캐싱을 시도합니다...");
            try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(S3_KEY)
                    .build())) {

                List<PlantDataDto> dtoList = objectMapper.readValue(inputStream, new TypeReference<>() {});
                plantMap.putAll(dtoList.stream()
                        .collect(Collectors.toMap(PlantDataDto::getId, Function.identity())));
                log.info("[Cache] S3로부터 {}개의 식물 데이터를 성공적으로 캐싱했습니다.", plantMap.size());
            }
        } catch (Exception s3Exception) {
            log.warn("[Cache-WARN] S3 캐싱 실패. 로컬 백업 파일로 전환합니다. (S3 오류: {})", s3Exception.getMessage());
            try {
                log.info("[Cache] 로컬 백업 파일 /data/final_plants.json 에서 캐싱을 시도합니다...");
                InputStream inputStream = new TypeReference<>() {}.getClass().getResourceAsStream("/data/final_plants.json");
                if (inputStream == null) {
                    throw new RuntimeException("로컬 백업 파일을 찾을 수 없습니다: /data/final_plants.json");
                }
                List<PlantDataDto> dtoList = objectMapper.readValue(inputStream, new TypeReference<>() {});
                plantMap.putAll(dtoList.stream()
                        .collect(Collectors.toMap(PlantDataDto::getId, Function.identity())));
                log.info("[Cache] 로컬 백업으로부터 {}개의 식물 데이터를 성공적으로 캐싱했습니다.", plantMap.size());
            } catch (Exception fallbackException) {
                log.error("[Cache-FATAL] S3와 로컬 백업 파일 모두 캐싱에 실패했습니다. 추천 시스템을 사용할 수 없습니다.", fallbackException);
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
