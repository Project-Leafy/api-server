package com.leafy.plant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 도감 이미지를 농사로 원본에서 복구해 저장소에 올린다.
 *
 * <p>도감 이미지(nongsaro_img/*)는 원래 S3 에 있었고 CloudFront 로 서빙됐다.
 * CloudFront 가 삭제돼 받을 수 없게 되어, 번들된 원본 데이터(nongsaro_plants.json)에 남은
 * 농사로 파일명으로 다시 받아온다. 추출 당시 첫 번째 파일을 대표 이미지로 썼으므로 같은 규칙을 따른다.
 *
 * <p>이미 저장소에 있는 이미지는 건너뛰므로 매번 기동해도 새로 받지 않는다.
 * 기동을 막지 않도록 별도 스레드에서 실행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.restore-plant-images", havingValue = "true", matchIfMissing = true)
public class PlantImageRestorer implements ApplicationRunner {

    private static final String NONGSARO_BASE = "http://www.nongsaro.go.kr/";
    private static final String KEY_MARKER = "/nongsaro_img/";

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        Thread worker = new Thread(this::restore, "plant-image-restorer");
        worker.setDaemon(true);
        worker.start();
    }

    void restore() {
        try {
            Map<String, String> originalByContentNo = loadOriginalFileUrls();
            JsonNode cleaned = readBundled("/data/nongsaro_cleaned.json");

            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            int total = 0, skipped = 0, restored = 0, failed = 0;
            for (JsonNode plant : cleaned) {
                String mainImgUrl = plant.path("mainImgUrl").asText("");
                int idx = mainImgUrl.indexOf(KEY_MARKER);
                if (idx < 0) {
                    continue;
                }
                total++;
                String key = mainImgUrl.substring(idx + 1); // nongsaro_img/<uuid>.<ext>

                if (fileStorageService.exists(key)) {
                    skipped++;
                    continue;
                }

                String source = originalByContentNo.get(plant.path("cntntsNo").asText());
                if (source == null) {
                    failed++;
                    log.warn("[PlantImages] 원본 파일 정보 없음 key={}", key);
                    continue;
                }

                try {
                    HttpResponse<byte[]> res = http.send(
                            HttpRequest.newBuilder(URI.create(source)).timeout(Duration.ofSeconds(30)).GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
                    if (res.statusCode() != 200 || res.body().length == 0) {
                        failed++;
                        log.warn("[PlantImages] 원본 다운로드 실패 key={} status={}", key, res.statusCode());
                        continue;
                    }
                    String contentType = res.headers().firstValue("Content-Type").orElse("image/jpeg");
                    fileStorageService.write(key, res.body(), contentType.split(";")[0]);
                    restored++;
                } catch (Exception e) {
                    failed++;
                    log.warn("[PlantImages] 복구 실패 key={} ({})", key, e.getMessage());
                }
            }

            log.info("[PlantImages] 도감 이미지 복구 완료 대상={} 이미있음={} 복구={} 실패={}",
                    total, skipped, restored, failed);
        } catch (Exception e) {
            log.error("[PlantImages] 도감 이미지 복구 중단 ({})", e.getMessage());
        }
    }

    /** 농사로 원본 데이터에서 콘텐츠 번호별 대표 이미지(첫 번째 파일)의 원본 주소를 만든다. */
    private Map<String, String> loadOriginalFileUrls() throws Exception {
        Map<String, String> result = new HashMap<>();
        for (JsonNode raw : readBundled("/data/nongsaro_plants.json")) {
            String cours = raw.path("rtnFileCours").asText("").split("\\|")[0];
            String file = raw.path("rtnStreFileNm").asText("").split("\\|")[0];
            if (!cours.isBlank() && !file.isBlank()) {
                result.put(raw.path("cntntsNo").asText(), NONGSARO_BASE + cours + "/" + file);
            }
        }
        return result;
    }

    private JsonNode readBundled(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("번들 데이터 없음: " + path);
            }
            return objectMapper.readTree(in);
        }
    }
}
