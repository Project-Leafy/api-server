package com.leafy.plant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.plant.dto.PlantDataDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.leafy.global.storage.FileStorageService;

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

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    /** 저장소에 보관되는 식물 데이터 파일의 키. */
    public static final String PLANT_DATA_KEY = "plants/final_plants.json";

    private final Map<Long, PlantDataDto> plantMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        // 1순위: 저장소에 있는 파일 (운영 중 갱신된 최신본)
        try (InputStream inputStream = fileStorageService.read(PLANT_DATA_KEY)) {
            if (inputStream != null) {
                cacheFrom(inputStream);
                log.info("[Cache] 저장소로부터 {}개의 식물 데이터를 캐싱했습니다.", plantMap.size());
                return;
            }
            log.info("[Cache] 저장소에 식물 데이터가 없어 번들된 기본 데이터를 사용합니다.");
        } catch (Exception e) {
            log.warn("[Cache-WARN] 저장소 읽기 실패. 번들된 기본 데이터로 전환합니다. (오류: {})", e.getMessage());
        }

        // 2순위: jar에 번들된 기본 데이터
        try (InputStream inputStream = getClass().getResourceAsStream("/data/final_plants.json")) {
            if (inputStream == null) {
                throw new IllegalStateException("번들된 데이터 파일을 찾을 수 없습니다: /data/final_plants.json");
            }
            cacheFrom(inputStream);
            log.info("[Cache] 번들 데이터로부터 {}개의 식물 데이터를 캐싱했습니다.", plantMap.size());
        } catch (Exception e) {
            log.error("[Cache-FATAL] 식물 데이터 캐싱에 모두 실패했습니다. 추천 시스템을 사용할 수 없습니다.", e);
        }
    }

    private void cacheFrom(InputStream inputStream) throws java.io.IOException {
        List<PlantDataDto> dtoList = objectMapper.readValue(inputStream, new TypeReference<>() {});
        plantMap.putAll(dtoList.stream()
                .collect(Collectors.toMap(PlantDataDto::getId, Function.identity())));
    }

    public PlantDataDto getPlantById(Long id) {
        return plantMap.get(id);
    }

    public Collection<PlantDataDto> getAllPlants() {
        return plantMap.values();
    }

    public void addPlant(PlantDataDto newPlant) {
        if (newPlant != null && newPlant.getId() != null) {
            plantMap.put(newPlant.getId(), newPlant);
            log.info("[Cache] 새로운 식물 '{}'(ID:{})가 메모리 내 캐시에 추가되었습니다.", newPlant.getKoreanName(), newPlant.getId());
        }
    }
}
