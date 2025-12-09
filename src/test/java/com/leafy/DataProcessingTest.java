package com.leafy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leafy.global.storage.S3UploadService;
import com.leafy.plant.service.AirPurificationJsonExtractor;
import com.leafy.plant.service.DataMergeService;
import com.leafy.plant.service.LlmContentService; // 추가됨
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@SpringBootTest(classes = {
        DataProcessingTest.TestConfig.class,
        AirPurificationJsonExtractor.class,
        DataMergeService.class,
        LlmContentService.class, // ✅ 서비스 등록
        S3UploadService.class
})
@TestPropertySource(properties = {
        // 🔑 OpenAI 키 (새로 발급받은 키 입력!)
        "openai.api-key=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",

        // 기존 키들 (그대로 유지)
        "purify.api-key=s0DiY85rrsnrj8jGwlsOEFuv5S/p0B0VPFIcxEFe17sWopBJW6BmDVtC7qsuODVGEhhOf3KGViLI5rPXLjy3xg==",
        "nongsaro.api-key=20251201XVUF79GLHPG8GYQHYNQBTA",
        "aws.s3.access-key=AKIA3BSVG2QAVDHVWAHC",
        "aws.s3.secret-key=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",
        "aws.s3.bucket-name=leafy-storage-unique-123",
        "cloudfront.url=[https://d2ahszyk1cjzyt.cloudfront.net](https://d2ahszyk1cjzyt.cloudfront.net)",

        "nongsaro.api.base-url=[http://api.nongsaro.go.kr/service/garden/gardenList](http://api.nongsaro.go.kr/service/garden/gardenList)",
        "nongsaro.api.detail-url=[http://api.nongsaro.go.kr/service/garden/gardenDtl](http://api.nongsaro.go.kr/service/garden/gardenDtl)",
        "nongsaro.api.image-base-url=[http://www.nongsaro.go.kr/](http://www.nongsaro.go.kr/)"
})
class DataProcessingTest {

    @Autowired private AirPurificationJsonExtractor airExtractor;
    @Autowired private DataMergeService mergeService;
    @Autowired private LlmContentService llmService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private S3UploadService s3UploadService;

    // 2. 농사로 데이터 청소 (선택 사항)
    @Test
    void cleanNongsaroData() {
        System.out.println(">>> 🧹 농사로 데이터 청소 시작 (Null/Empty/Code, 이미지경로 필드 제거)...");

        try {
            File inputFile = new File("src/main/resources/data/nongsaro_plants.json");
            if (!inputFile.exists()) {
                System.out.println("❌ 원본 파일이 없습니다.");
                return;
            }

            JsonNode rootNode = objectMapper.readTree(inputFile);
            int removedFieldsCount = 0;

            List<String> removeKeys = List.of(
                    "rtnFileCours", "rtnStreFileNm", "rtnImageDc", "rtnThumbFileNm"
            );

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    if (node.isObject()) {
                        ObjectNode objNode = (ObjectNode) node;
                        // 필드들을 순회하며 삭제할 것 찾기 (Iterator 사용 필수)
                        var fieldIterator = objNode.fields();

                        while (fieldIterator.hasNext()) {
                            var entry = fieldIterator.next();
                            String key = entry.getKey();
                            JsonNode value = entry.getValue();

                            // 1. 값이 없거나(null) 빈 문자열("")인 경우 삭제
                            if (value.isNull() || value.asText().trim().isEmpty()) {
                                fieldIterator.remove();
                                removedFieldsCount++;
                                continue;
                            }

                            // 2. 키 이름이 'Code'로 끝나는 경우 삭제 (예: smellCode)
                            // 단, 'CodeNm'은 남겨야 함 (예: smellCodeNm - 이건 한글 설명임)
                            if (key.endsWith("Code")) {
                                fieldIterator.remove();
                                removedFieldsCount++;
                            }

                            // 3. removeKeys 리스트에 포함된 이미지 관련 필드 삭제
                            if (removeKeys.contains(key)) {
                                fieldIterator.remove();
                                removedFieldsCount++;
                            }
                        }
                    }
                }
            }

            // 결과 저장
            File outputFile = new File("src/main/resources/data/nongsaro_cleaned.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, rootNode);

            System.out.println("✅ 청소 완료!");
            System.out.println("   - 삭제된 필드 수: " + removedFieldsCount + "개");
            System.out.println("   - 생성된 파일: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 3. 공기정화 데이터 청소 (추가됨)
    @Test
    void cleanAirPurificationData() {
        System.out.println(">>> 🧹 공기정화 데이터 청소 시작 (HTML 태그 제거 & Null 필드 정리)...");
        try {
            File inputFile = new File("src/main/resources/data/air_purification_plants.json");
            if (!inputFile.exists()) {
                System.out.println("❌ 원본 파일이 없습니다.");
                return;
            }

            JsonNode rootNode = objectMapper.readTree(inputFile);
            int removedFieldsCount = 0;

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    if (node.isObject()) {
                        ObjectNode objNode = (ObjectNode) node;

                        // 1. HTML 태그 제거 (htmlContent 필드 정제)
                        if (objNode.has("htmlContent")) {
                            String html = objNode.get("htmlContent").asText();
                            // HTML 태그 제거 정규식 (<...>)
                            String cleanText = html.replaceAll("<[^>]*>", " ")
                                    .replaceAll("&nbsp;", " ")
                                    .replaceAll("\\s+", " ") // 연속 공백 제거
                                    .trim();
                            objNode.put("htmlContent", cleanText); // 정제된 텍스트로 덮어쓰기
                        }

                        // 2. Null 또는 빈 값 필드 삭제
                        Iterator<Map.Entry<String, JsonNode>> fieldIterator = objNode.fields();
                        while (fieldIterator.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fieldIterator.next();
                            JsonNode value = entry.getValue();

                            // 값이 없거나, 빈 문자열이거나, 빈 객체({})인 경우 삭제
                            if (value.isNull() ||
                                    (value.isTextual() && value.asText().trim().isEmpty()) ||
                                    (value.isObject() && value.isEmpty())) {

                                fieldIterator.remove();
                                removedFieldsCount++;
                            }
                        }
                    }
                }
            }

            File outputFile = new File("src/main/resources/data/air_purification_cleaned.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, rootNode);
            System.out.println("✅ 청소 완료! 삭제된 필드 수: " + removedFieldsCount);
            System.out.println("📂 결과 파일: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

     //1. 전체 파이프라인 실행
    @Test
    void processFullDataPipeline() {
        System.out.println(">>> 🚀 데이터 파이프라인 가동!");

        // 1. 공기정화 데이터 (이미 있으면 주석 처리 가능)
        // System.out.println(">>> [1/3] 공기정화 데이터 추출...");
        // airExtractor.extractAndUpload();

        // 2. 데이터 병합
//        System.out.println(">>> [2/3] 데이터 병합 (농사로 + 공기정화)...");
//        mergeService.mergeData();

        // 3. LLM 가공 및 최종 업로드 (핵심!)
        System.out.println(">>> [3/3] AI 데이터 정제 및 최종 S3 업로드...");
        llmService.processAndUpload();

        System.out.println(">>> 🎉 모든 작업 완료! S3의 final_plants.json을 확인하세요.");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean public WebClient webClient() {
            return WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(20 * 1024 * 1024)).build()).build();
        }
        @Bean public S3Client s3Client(@Value("${aws.s3.access-key}") String accessKey, @Value("${aws.s3.secret-key}") String secretKey) {
            return S3Client.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))).build();
        }
    }
}