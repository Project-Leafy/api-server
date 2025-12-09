package com.leafy.plant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leafy.global.storage.S3UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMergeService {

    private final ObjectMapper objectMapper;
    private final S3UploadService s3UploadService;

    @Value("${openai.api-key}")
    private String openAiKey;

    // 성능과 안정성을 위해 mini 사용 (비용 절감)
    private static final String MODEL = "gpt-4o";

    record PlantIdentity(String id, String names) {}

    public void mergeData() {
        log.info("🧠 [AI 병합] 식물 데이터 지능형 병합 시작 (배치 처리 적용)...");

        try {
            // 1. 파일 읽기
            File mainFile = new File("src/main/resources/data/nongsaro_cleaned.json");
            File airFile = new File("src/main/resources/data/air_purification_cleaned.json");

            if (!mainFile.exists() || !airFile.exists()) {
                log.error("❌ 데이터 파일이 없습니다.");
                return;
            }

            JsonNode nongsaroNodes = objectMapper.readTree(mainFile);
            JsonNode airNodes = objectMapper.readTree(airFile);

            // 2. 데이터 리스트화
            List<PlantIdentity> nongsaroList = new ArrayList<>();
            Map<String, JsonNode> nongsaroMap = new HashMap<>();
            if (nongsaroNodes.isArray()) {
                for (JsonNode node : nongsaroNodes) {
                    String id = node.path("cntntsNo").asText();
                    // 이름 문자열 구성 (특수문자 제거로 JSON 오류 방지)
                    String names = String.format("학명:%s, 국명:%s, 유통명:%s",
                                    node.path("plntbneNm").asText(),
                                    node.path("cntntsSj").asText(),
                                    node.path("distbNm").asText())
                            .replace("\"", "") // 따옴표 제거 안전장치
                            .replace("\n", " ");
                    nongsaroList.add(new PlantIdentity(id, names));
                    nongsaroMap.put(id, node);
                }
            }

            List<PlantIdentity> airList = new ArrayList<>();
            Map<String, JsonNode> airMap = new HashMap<>();
            if (airNodes.isArray()) {
                for (JsonNode node : airNodes) {
                    String id = node.path("idx").asText();
                    String names = String.format("Title:%s, 학명:%s",
                                    node.path("title").asText(),
                                    node.path("scientificName").asText())
                            .replace("\"", "")
                            .replace("\n", " ");
                    airList.add(new PlantIdentity(id, names));
                    airMap.put(id, node);
                }
            }

            // 3. 배치 처리 (Batch Processing) - 30개씩 끊어서 요청
            int batchSize = 30;
            List<MergedPair> totalMatches = new ArrayList<>();
            Set<String> matchedNongsaroIds = new HashSet<>();
            Set<String> matchedAirIds = new HashSet<>();

            log.info("총 {}개의 농사로 데이터를 {}개씩 나누어 처리합니다.", nongsaroList.size(), batchSize);

            for (int i = 0; i < nongsaroList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, nongsaroList.size());
                List<PlantIdentity> batchList = nongsaroList.subList(i, end);

                log.info("🔄 배치 처리 중 ({}/{})...", end, nongsaroList.size());

                // GPT 호출 (배치 리스트 vs 전체 공기정화 리스트)
                MergeResult batchResult = callGptForMerging(batchList, airList);

                if (batchResult != null && batchResult.matches != null) {
                    for (MergedPair pair : batchResult.matches) {
                        totalMatches.add(pair);
                        matchedNongsaroIds.add(pair.nongsaroId);
                        matchedAirIds.add(pair.airId);
                    }
                }

                // API 속도 제한 방지 대기
                Thread.sleep(5000);
            }

            // 4. 결과 조립
            ArrayNode finalArray = objectMapper.createArrayNode();
            long newIdCounter = 1;

            // 4-1. 매칭된 데이터
            for (MergedPair pair : totalMatches) {
                ObjectNode newPlant = createBaseObject(newIdCounter++, pair.koreanName, pair.scientificName);

                JsonNode nData = nongsaroMap.get(pair.nongsaroId);
                JsonNode aData = airMap.get(pair.airId);

                if (nData != null) newPlant.set("nongsaroData", nData);
                if (aData != null) newPlant.set("airInfo", aData);

                // 이미지 선택
                String imgUrl = null;
                if (nData != null && hasImage(nData)) imgUrl = nData.get("mainImgUrl").asText();
                if (imgUrl == null && aData != null && hasImage(aData)) imgUrl = aData.get("mainImgUrl").asText();
                newPlant.put("imageUrl", imgUrl);

                finalArray.add(newPlant);
            }

            // 4-2. 농사로 단독 (매칭 안 된 것들)
            int nOnlyCount = 0;
            for (PlantIdentity item : nongsaroList) {
                if (!matchedNongsaroIds.contains(item.id)) {
                    JsonNode nData = nongsaroMap.get(item.id);
                    String name = cleanName(nData.path("cntntsSj").asText());
                    if (name.isEmpty()) name = cleanName(nData.path("distbNm").asText());
                    String sciName = nData.path("plntbneNm").asText();

                    ObjectNode newPlant = createBaseObject(newIdCounter++, name, sciName);
                    newPlant.set("nongsaroData", nData);
                    if (hasImage(nData)) newPlant.put("imageUrl", nData.get("mainImgUrl").asText());

                    finalArray.add(newPlant);
                    nOnlyCount++;
                }
            }

            // 4-3. 공기정화 단독 (매칭 안 된 것들)
            int aOnlyCount = 0;
            for (PlantIdentity item : airList) {
                if (!matchedAirIds.contains(item.id)) {
                    JsonNode aData = airMap.get(item.id);
                    String name = cleanName(aData.path("title").asText());
                    String sciName = aData.path("scientificName").asText();

                    ObjectNode newPlant = createBaseObject(newIdCounter++, name, sciName);
                    newPlant.set("airInfo", aData);
                    if (hasImage(aData)) newPlant.put("imageUrl", aData.get("mainImgUrl").asText());

                    finalArray.add(newPlant);
                    aOnlyCount++;
                }
            }

            // 5. 저장 및 업로드
            File resultFile = new File("src/main/resources/data/merge_plants.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultFile, finalArray);

            log.info("✅ 병합 완료! 총 {}개 (매칭: {}, 농사로단독: {}, 공기정화단독: {})",
                    finalArray.size(), totalMatches.size(), nOnlyCount, aOnlyCount);

            try (FileInputStream fis = new FileInputStream(resultFile)) {
                String s3Url = s3UploadService.upload(fis, "merge_plants.json", resultFile.length(), "application/json", "plants");
                log.info("🚀 S3 업로드 완료: {}", s3Url);
            }

        } catch (Exception e) {
            log.error("❌ 데이터 병합 중 오류", e);
        }
    }

    private boolean hasImage(JsonNode node) {
        return node.has("mainImgUrl") && !node.get("mainImgUrl").isNull() && !node.get("mainImgUrl").asText().isEmpty();
    }

    private String cleanName(String raw) {
        if (raw == null) return "이름 없음";
        return raw.split("\\(")[0].trim();
    }

    private ObjectNode createBaseObject(long id, String korName, String sciName) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("koreanName", korName);
        node.put("scientificName", sciName);
        return node;
    }

    private MergeResult callGptForMerging(List<PlantIdentity> nListBatch, List<PlantIdentity> fullAirList) {
        try {
            String systemPrompt = """
                너는 식물 분류학 전문가야. 'List A'의 식물들이 'List B'에 존재하는지 확인해.
                
                [규칙]
                1. 학명(Scientific Name)이나 국문명이 일치하면 매칭된 것으로 간주해.
                2. **List A에 있는 항목 중, List B와 매칭되는 것만** JSON으로 반환해.
                3. 매칭되지 않는 것은 무시해. 
                4. 결과의 `koreanName`, `scientificName`은 가장 정확한 표준명으로 정제해.
                5. **절대 동일한 종이 아닌 식물을 동일하다고 착각하는 오류를 범하지마**
                
                응답 포맷: { "matches": [ { "nongsaroId": "...", "airId": "...", "koreanName": "...", "scientificName": "..." } ] }
                """;

            // List A는 이번 배치(30개), List B는 전체(64개)
            String userPrompt = String.format("""
                [List A (Targets)] %s
                [List B (Reference)] %s
                """,
                    objectMapper.writeValueAsString(nListBatch),
                    objectMapper.writeValueAsString(fullAirList)
            );

            WebClient client = WebClient.create("https://api.openai.com/v1");

            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4o",
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.0
            );

            String response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openAiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            return objectMapper.readValue(content, MergeResult.class);

        } catch (Exception e) {
            log.error("GPT 호출 실패 (배치 처리 중): {}", e.getMessage());
            return null; // 실패 시 해당 배치는 매칭 없음으로 처리하고 진행
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MergeResult {
        public List<MergedPair> matches = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MergedPair {
        public String nongsaroId;
        public String airId;
        public String koreanName;
        public String scientificName;
    }
}