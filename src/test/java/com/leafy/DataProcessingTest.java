package com.leafy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.global.storage.S3UploadService;
import com.leafy.plant.service.AirPurificationJsonExtractor;
import com.leafy.plant.service.DataMergeService;
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

@SpringBootTest(classes = {
        DataProcessingTest.TestConfig.class, // 수동 설정
        AirPurificationJsonExtractor.class,  // 1. 공기정화 추출기
        DataMergeService.class,              // 2. 데이터 병합기
//        NongsaroJsonExtractor.class,          (필요 시) 농사로 추출기
        S3UploadService.class                // S3 업로더
})
@TestPropertySource(properties = {
        // ==========================================
        // 🔑 [필수] 키 값을 여기에 다시 넣어주세요!
        // ==========================================
        "nongsaro.api-key=20251201XVUF79GLHPG8GYQHYNQBTA",
        "aws.s3.access-key=AKIA3BSVG2QAVDHVWAHC", // 변수명 주의
        "aws.s3.secret-key=RdS9VldRu6kfBT6RHtkW3mM5dko5HeRrhzDClPUT", // 변수명 주의
        "aws.s3.bucket-name=leafy-storage-unique-123",
        "cloudfront.url=https://d2ahszyk1cjzyt.cloudfront.net",
        "purify.api-key=s0DiY85rrsnrj8jGwlsOEFuv5S/p0B0VPFIcxEFe17sWopBJW6BmDVtC7qsuODVGEhhOf3KGViLI5rPXLjy3xg==",

        // API 설정
        "nongsaro.api.base-url=http://api.nongsaro.go.kr/service/garden/gardenList",
        "nongsaro.api.detail-url=http://api.nongsaro.go.kr/service/garden/gardenDtl",
        "nongsaro.api.image-base-url=http://www.nongsaro.go.kr/"
})
class DataProcessingTest {

    @Autowired
    private AirPurificationJsonExtractor airExtractor;

    @Autowired
    private DataMergeService mergeService;

    @Test
    void processAllData() {
        System.out.println(">>> 🚀 [1단계] 공기정화 데이터 추출 시작");
        airExtractor.extractAndUpload(); // air_purification_plants.json 생성

        System.out.println(">>> 🚀 [2단계] 데이터 병합 시작 (농사로 + 공기정화)");
        mergeService.mergeData(); // merge_plants.json 생성 및 업로드

        System.out.println(">>> ✅ 모든 데이터 작업 완료!");
    }

    // 설정 충돌 방지를 위한 수동 Config
    @TestConfiguration
    static class TestConfig {

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public S3Client s3Client(
                @Value("${aws.s3.access-key}") String accessKey,
                @Value("${aws.s3.secret-key}") String secretKey
        ) {
            return S3Client.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .build();
        }

        @Bean
        public WebClient webClient() {
            return WebClient.builder()
                    .exchangeStrategies(ExchangeStrategies.builder()
                            .codecs(c -> c.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                            .build())
                    .build();
        }
    }
}