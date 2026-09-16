package com.leafy.plant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leafy.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmContentService {

    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;

    @Value("${openai.api-key}")
    private String openAiKey;

    private static final String MODEL = "gpt-4o";

    public void processAndUpload() {
        log.info("🧠 [LLM] 식물 데이터 최종 구축 시작 (전략: 표 기반 정밀 매핑)...");

        try {
            File mergedFile = new File("src/main/resources/data/merge_plants.json");
            if (!mergedFile.exists()) {
                log.error("❌ 병합된 파일이 없습니다.");
                return;
            }

            JsonNode rootNode = objectMapper.readTree(mergedFile);
            List<ObjectNode> finalList = new java.util.ArrayList<>();
            int count = 0;

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    // 1. 식별 정보 (ID는 루프 돌면서 1부터 새로 부여, 나머지는 GPT가 결정)
                    long newId = count + 1;

                    // GPT에게 줄 참고 자료 (전체 데이터)
                    String rawData = node.toString();
                    String tempName = node.path("koreanName").asText();
                    String tempSci = node.path("scientificName").asText(); // 학명 추가

                    log.info("🤖 [{}/{}] GPT 생성 중: {}({})", newId, rootNode.size(), tempName, tempSci);

                    // 2. GPT 호출 (인자 개수 수정됨)
                    LlmResponse gptData = callGpt(tempName, tempSci, rawData);

                    if (gptData != null) {
                        ObjectNode finalNode = objectMapper.createObjectNode();

                        // [Identity]
                        finalNode.put("id", newId);
                        finalNode.put("koreanName", gptData.koreanName); // GPT 검수 완료된 이름
                        finalNode.put("scientificName", gptData.scientificName); // GPT 검수 완료된 학명

                        // [Image] Logic: 농사로 > 공기정화 > null
                        String imgUrl = null;
                        if (node.has("imageUrl") && !node.get("imageUrl").isNull()) {
                            imgUrl = node.get("imageUrl").asText();
                        } else if (node.has("airInfo") && node.get("airInfo").has("mainImgUrl")) {
                            imgUrl = node.get("airInfo").get("mainImgUrl").asText();
                        }
                        finalNode.put("imageUrl", imgUrl);

                        // [Description]
                        finalNode.put("description", gptData.description);
                        finalNode.put("flowerFruitInfo", gptData.flowerFruitInfo);

                        // [Environment & Care]
                        finalNode.put("lightLux", gptData.lightLux);
                        finalNode.put("sunlightTip", gptData.sunlightTip);
                        finalNode.put("temp", gptData.temp);
                        finalNode.put("tempTip", gptData.tempTip);
                        finalNode.put("humidity", gptData.humidity);
                        finalNode.put("humidityTip", gptData.humidityTip);
                        finalNode.put("soil", gptData.soil);

                        // [Cycle & Notification]
                        finalNode.put("waterSpring", gptData.waterSpring);
                        finalNode.put("waterSummer", gptData.waterSummer);
                        finalNode.put("waterAutumn", gptData.waterAutumn);
                        finalNode.put("waterWinter", gptData.waterWinter);
                        finalNode.put("waterTip", gptData.waterTip);

                        finalNode.put("fertilizerCycleDays", gptData.fertilizerCycleDays);
                        finalNode.put("fertilizerTip", gptData.fertilizerTip);

                        finalNode.put("repottingCycleYears", gptData.repottingCycleYears);
                        finalNode.put("repottingTip", gptData.repottingTip);

                        // [Specs]
                        finalNode.put("growthSpeed", gptData.growthSpeed);
                        finalNode.put("displayDifficulty", gptData.displayDifficulty);

                        finalNode.put("isToxic", gptData.isToxic);
                        finalNode.put("toxicityInfo", gptData.toxicityInfo);

                        finalNode.put("airPurificationKeywords", convertToString(gptData.airPurificationKeywords));
                        finalNode.put("airPurificationInfo", gptData.airPurificationInfo);

                        finalNode.set("checkPointList", objectMapper.valueToTree(gptData.checkPointList));
                        finalNode.set("keywordTags", objectMapper.valueToTree(gptData.keywordTags));

                        // 개별 파일 S3 업로드 (백업용)
                        String safeFileName = gptData.scientificName
                                .replaceAll("[^a-zA-Z0-9\\s]", "")
                                .trim()
                                .replace(" ", "_") + ".json";
                        if (safeFileName.isEmpty()) safeFileName = "plant_" + newId + ".json";

                        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(finalNode);
                        log.info("✅ [ID:{}] 생성 성공! (파일명: {})\n{}", newId, safeFileName, prettyJson);

                        uploadIndividualToS3(finalNode, safeFileName);

                        finalList.add(finalNode);
                    }

                    count++;
                    Thread.sleep(3000);
                }
            }

            File finalFile = new File("src/main/resources/data/final_plants.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(finalFile, finalList);
            log.info("✅ 최종 JSON 생성 완료 ({}개)", finalList.size());

            try (FileInputStream fis = new FileInputStream(finalFile)) {
                String s3Url = fileStorageService.uploadFixed(
                        fis, "final_plants.json", finalFile.length(), "application/json", "plants"
                );
                log.info("🚀 S3 업로드 성공! URL: {}", s3Url);
            }

        } catch (Exception e) {
            log.error("❌ LLM 작업 실패", e);
        }
    }

    private void uploadIndividualToS3(ObjectNode jsonNode, String fileName) {
        try {
            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(jsonNode);
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            fileStorageService.uploadFixed(bis, fileName, bytes.length, "application/json", "plants");
        } catch (Exception e) {
            log.error("⚠️ 업로드 실패 (파일: {})", fileName, e);
        }
    }

    private String convertToString(Object input) {
        if (input == null) return "정보 없음";
        if (input instanceof List) {
            return String.join(", ", (List<String>) input);
        }
        return input.toString();
    }

    private LlmResponse callGpt(String name, String sciName, String rawContext) {
        // ✨ [핵심] 사용자가 제공한 표 내용을 그대로 프롬프트화
        String systemPrompt = """
            너는 식물학 전문가야. 입력된 JSON 데이터에는 농사로 정보(N)인 'nongsaroData'와 공기정화 정보(A)인 'airInfo'가 포함되어 있어. 제공된 '농사로(N)'와 '공기정화(A)' 데이터를 분석하고, 네 지식을 더해서 JSON을 완성해. 마크다운 없이 순수 JSON 문자열만 반환해. 문장은 해요체로 작성해줘
            
            [필드별 생성 규칙]
            1. koreanName: N.distbNm, A.title 중 가장 대중적인 한글 이름 하나 선택. (괄호/특수문자 제거)
            2. scientificName: N.plntbneNm, A.scientificName 중 표준 학명 선택 및 오타 수정.
            3. description: N.fncltyInfo, N.adviseInfo, A.content 등을 종합해서 위키백과 스타일 3문장.
            4. flowerFruitInfo: N.lefStleInfo, N.prpgtEraInfo 등 참고하여 꽃/열매 특징 설명.
            
            5. lightLux: N.lighttdemanddoCodeNm 데이터 사용.
            6. sunlightTip: 광도 조건에 맞춰 직사광선 주의 등 구체적 조언 1문장.
            7. temp: N.grwhTpCodeNm 데이터 사용.
            8. tempTip: N.winterLwetTpCodeNm 참고하여 월동 온도 주의사항 1문장.
            9. humidity: N.hdCodeNm 데이터 사용.
            10. humidityTip: 건조 시 분무 필요 여부 등 습도 관리 팁 1문장.
            11. soil: N.soilInfo 데이터 다듬어서 사용(없으면 지식 활용, 예시:배수가 잘 되는 상토).
            
            12. **[중요] waterSpring, waterSummer, waterAutumn, waterWinter**:
                - 농사로 데이터의 계절별'watercy....CodeNm' 텍스트(봄은 watercycleSpringCodeNm)를 분석하여 각 계절별 물주기 주기를 **반드시 '일(day)' 단위 정수(Integer)**로 변환해서 각각 별도의 필드로 반환해.
                - 변환 기준: '항상 축축'->3, '표면 마름'->7, '대부분 마름'->14, '바짝 마름'->30. 해당안될시 네 지식으로 생성해
                - (예시 출력: "waterSpring": 7, "waterSummer": 5, "waterAutumn": 7, "waterWinter": 10)
            13. waterTip: 저면관수, 과습주의 등 물주기 꿀팁 1문장.
            14. fertilizerCycleDays: N.frtlzrInfo 보고 숫자 변환. (예시: 보통 30, 많음 14, 없음 0 등)
            15. fertilizerTip: 비료 종류(액비 등)와 방법, 양 등 구체적 제시한 1문장
            16. repottingCycleYears: N.grwtveCodeNm(성장속도) 보고 판단후 숫자 변환.(느림은 1)
            17. repottingTip: 분갈이 신호(뿌리 나옴 등) 포함 팁 1문장.
            
            18. growthSpeed: N.grwtveCodeNm 사용 (없으면 '느림/보통/빠름' 중 하나 선택).
            19. displayDifficulty: N.managelevelCodeNm 사용 (없으면 '초보자/경험자/전문가' 중 하나 선택).
            20. isToxic: N.toxctyInfo 보고 판단 (true/false).
            21. toxicityInfo: 구체적 독성 성분과 증상 서술한 최대 3문장.
            22. airPurificationKeywords: A.purificationMap, N.fncltyInfo 참고하여 해당 식물이 제거하는 공기 오염 주요 물질 쉼표로 나열.
            23. airPurificationInfo: airPurificationKeywords필드 사용하되 비어있으면 '공기정화 능력이 미미해요' 등 설명, 있으면 능력 설명 (최대 3문장).
            24. checkPointList: 핵심 체크포인트 3가지를 각 1문장씩 서술(3문장).
            25. keywordTags: 해시태그 3~4개 (#거실화분 등).
            """;

        String userPrompt = String.format(
                "식물명: %s (%s)\n참고 데이터: %s",
                name, sciName, rawContext
        );

        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.3
        );

        WebClient client = WebClient.create("https://api.openai.com/v1");
        // 2. 재시도 로직 (최대 5번)
        int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
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
                return objectMapper.readValue(content, LlmResponse.class);

            } catch (WebClientResponseException.TooManyRequests e) {
                // 429 에러 발생 시: 점진적으로 대기 시간 늘리며 재시도 (2초, 4초, 6초...)
                long waitTime = attempt * 5000L;
                log.warn("⚠️ 429 Too Many Requests! {}초 대기 후 재시도 ({}/{}) - 식물: {}", waitTime / 1000, attempt, maxRetries, name);
                try { Thread.sleep(waitTime); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                // 다른 에러는 바로 실패 처리
                log.warn("❌ GPT 호출 중 오류 (식물: {}) - {}", name, e.getMessage());
                return null;
            }
        }
        log.error("❌ 재시도 횟수 초과. 건너뜁니다. (식물: {})", name);
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LlmResponse {
        public String koreanName;
        public String scientificName;

        public String description;
        public String flowerFruitInfo;

        public int waterSpring;
        public int waterSummer;
        public int waterAutumn;
        public int waterWinter;
        public String waterTip;

        public String lightLux;
        public String sunlightTip;
        public String temp;
        public String tempTip;
        public String humidity;
        public String humidityTip;
        public String soil;

        public int fertilizerCycleDays;
        public String fertilizerTip;
        public int repottingCycleYears;
        public String repottingTip;

        public String growthSpeed;
        public String displayDifficulty;

        public boolean isToxic;
        public String toxicityInfo;

        public Object airPurificationKeywords;
        public String airPurificationInfo;

        public List<String> checkPointList;
        public List<String> keywordTags;
    }
}