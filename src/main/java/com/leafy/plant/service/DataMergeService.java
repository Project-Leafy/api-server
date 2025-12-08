package com.leafy.plant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leafy.global.storage.S3UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMergeService {

    private final ObjectMapper objectMapper;
    private final S3UploadService s3UploadService;

    public void mergeData() {
        log.info("🔄 데이터 병합 작업 시작 (농사로 + 공기정화)...");
        try {
            // 1. JSON 파일 경로 설정 (nongsaro 폴더 내)
            File mainFile = new File("src/main/resources/data/nongsaro_plants.json");
            File airFile = new File("src/main/resources/data/air_purification_plants.json");

            // 파일 존재 여부 체크
            if (!mainFile.exists()) {
                log.error("❌ 농사로 데이터 파일이 없습니다: {}", mainFile.getAbsolutePath());
                return;
            }
            if (!airFile.exists()) {
                log.error("❌ 공기정화 데이터 파일이 없습니다. (추출 먼저 진행해주세요): {}", airFile.getAbsolutePath());
                return;
            }

            // 2. JSON 읽기
            JsonNode mainNode = objectMapper.readTree(mainFile);
            JsonNode airNode = objectMapper.readTree(airFile);

            // 3. 공기정화 데이터를 검색하기 편하게 Map으로 변환 (Key: 공백 제거한 식물명)
            Map<String, JsonNode> airMap = new HashMap<>();
            if (airNode.isArray()) {
                for (JsonNode node : airNode) {
                    // "title"(공기정화) 또는 "cntntsSj"(농사로) 필드를 이름으로 사용
                    String name = node.has("title") ? node.get("title").asText() : node.get("cntntsSj").asText();
                    // 매칭률을 높이기 위해 공백 제거 (예: '고무 나무' == '고무나무')
                    String cleanName = name.trim().replace(" ", "");
                    airMap.put(cleanName, node);
                }
            }

            // 4. 병합 (Main 데이터에 Air 정보 끼워넣기)
            int matchCount = 0;
            if (mainNode.isArray()) {
                for (JsonNode node : mainNode) {
                    String name = node.get("cntntsSj").asText();
                    String cleanName = name.trim().replace(" ", "");

                    // 매칭되는 데이터가 있으면 'airInfo' 필드에 통째로 추가
                    if (airMap.containsKey(cleanName)) {
                        JsonNode airInfo = airMap.get(cleanName);
                        ((ObjectNode) node).set("airInfo", airInfo); // airInfo라는 이름으로 추가
                        matchCount++;
                    } else {
                        ((ObjectNode) node).putNull("airInfo"); // 없으면 null 처리
                    }
                }
            }

            // 5. 병합된 파일 저장
            File resultFile = new File("src/main/resources/data/merge_plants.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultFile, mainNode);

            log.info("✅ 병합 완료! (총 {}개 중 {}개 매칭 성공)", mainNode.size(), matchCount);
            log.info("💾 파일 저장됨: {}", resultFile.getAbsolutePath());

            // 6. S3 업로드
            try (FileInputStream fis = new FileInputStream(resultFile)) {
                String s3Url = s3UploadService.upload(
                        fis,
                        resultFile.getName(),
                        resultFile.length(),
                        "application/json",
                        "plants"
                );
                log.info("📤 병합 파일 S3 업로드 완료: {}", s3Url);
            }

        } catch (Exception e) {
            log.error("❌ 데이터 병합 중 오류 발생", e);
        }
    }
}