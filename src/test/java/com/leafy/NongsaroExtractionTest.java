package com.leafy;

import com.leafy.global.storage.S3UploadService;
import com.leafy.plant.service.NongsaroJsonExtractor;
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
import java.io.FileInputStream;

@SpringBootTest(classes = {
        NongsaroExtractionTest.TestConfig.class, // 👇 수동 설정 (S3Client 직접 생성)
        NongsaroJsonExtractor.class,            // 테스트 대상
        S3UploadService.class                   // 업로드 서비스
        // ⚠️ AWS AutoConfiguration 관련 클래스들을 모두 제거했습니다. (충돌 방지)
})
@TestPropertySource(properties = {
        // ==========================================
        // 🔑 [필수] 발급받은 키 값을 여기에 붙여넣으세요
        // ==========================================
        "nongsaro.api-key=20251201XVUF79GLHPG8GYQHYNQBTA",
        "aws.s3.access-key=AKIA3BSVG2QAVDHVWAHC", // 변수명 주의
        "aws.s3.secret-key=RdS9VldRu6kfBT6RHtkW3mM5dko5HeRrhzDClPUT", // 변수명 주의
        "aws.s3.bucket-name=leafy-storage-unique-123",
        "cloudfront.url=https://d2ahszyk1cjzyt.cloudfront.net",

        // 농사로 설정 (이미지 URL 중복 방지를 위해 도메인만 남김)
        "nongsaro.api.base-url=http://api.nongsaro.go.kr/service/garden/gardenList",
        "nongsaro.api.detail-url=http://api.nongsaro.go.kr/service/garden/gardenDtl",
        "nongsaro.api.image-base-url=http://www.nongsaro.go.kr/"
})
class NongsaroExtractionTest {

    @Autowired
    private NongsaroJsonExtractor extractor;

    @Autowired // S3 업로더 주입
    private S3UploadService s3UploadService;

    @Test
    void runManualExtraction() {
        System.out.println(">>> 🚀 수동 데이터 추출 시작 (S3 강제 연결 모드)");

        // 1. 기존 로직: 데이터 추출 & 이미지 업로드 & 로컬 JSON 생성
        extractor.extractDataToFile();

        System.out.println(">>> ✅ 데이터 추출 및 로컬 JSON 생성 완료!");

        // 👇 2. [추가됨] 생성된 JSON 파일을 S3에 업로드하는 로직
        uploadJsonToS3();
    }
    private void uploadJsonToS3() {
        try {
            // 로컬에 생성된 파일 경로
            File file = new File("src/main/resources/data/nongsaro_plants.json");

            if (!file.exists()) {
                System.err.println("❌ 파일이 없습니다: " + file.getAbsolutePath());
                return;
            }

            System.out.println(">>> 📤 JSON 파일 S3 업로드 시작...");

            try (FileInputStream inputStream = new FileInputStream(file)) {
                // S3UploadService 재활용 ("nongsaro" 폴더에 업로드)
                String s3Url = s3UploadService.upload(
                        inputStream,
                        file.getName(),
                        file.length(),
                        "application/json",
                        "nongsaro" // S3 내 폴더명
                );
                System.out.println(">>> 🎉 JSON 업로드 성공! URL: " + s3Url);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ JSON 업로드 실패");
        }
    }

    @TestConfiguration
    static class TestConfig {

        // 1. ✅ S3Client 수동 생성 (자동 설정 오류 해결의 핵심)
        @Bean
        public S3Client s3Client(
                @Value("${aws.s3.access-key}") String accessKey,
                @Value("${aws.s3.secret-key}") String secretKey
        ) {
            return S3Client.builder()
                    .region(Region.AP_NORTHEAST_2) // 서울 리전 고정
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .build();
        }

        // 2. WebClient 수동 생성 (수정됨: 파라미터 제거)
        @Bean
        public WebClient webClient() {
            // 👇 매개변수로 Builder를 받지 않고, 여기서 직접 생성합니다!
            return WebClient.builder()
                    .exchangeStrategies(ExchangeStrategies.builder()
                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024)) // 20MB
                            .build())
                    .build();
        }
    }
}