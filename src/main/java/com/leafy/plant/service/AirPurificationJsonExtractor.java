package com.leafy.plant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.leafy.global.storage.S3UploadService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AirPurificationJsonExtractor {

    private final WebClient webClient;
    private final XmlMapper xmlMapper = new XmlMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final S3UploadService s3UploadService;

    @Value("${purify.api-key}")
    private String apiKey;

    private final String BASE_URL = "https://apis.data.go.kr/1390804/NihhsFuriAirInfo";

    public void extractAndUpload() {
        log.info("🌬️ 공기정화 식물 데이터 추출 및 파싱 시작...");
        List<AirDetailDto> collectedData = new ArrayList<>();

        try {
            URI listUri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/selectPuriAirPlantList")
                    .queryParam("serviceKey", apiKey)
                    .queryParam("numOfRows", "300")
                    .queryParam("pageNo", "1")
                    .queryParam("searchType", "1")
                    .build()
                    .toUri();

            String xmlResponse = webClient.get().uri(listUri).retrieve().bodyToMono(String.class).block();
            AirListResponse listResponse = xmlMapper.readValue(xmlResponse, AirListResponse.class);

            // ✅ [수정] 이중 구조 반영: getRoot().getResultList()
            if (listResponse != null && listResponse.getRoot() != null && listResponse.getRoot().getResultList() != null) {
                List<AirListItem> items = listResponse.getRoot().getResultList();
                log.info("📋 공기정화 목록 조회 성공: 총 {}건", items.size());

                int count = 0;
                for (AirListItem item : items) {
                    AirDetailDto detail = fetchDetailAndParse(item.getIdx());
                    if (detail != null) {
                        collectedData.add(detail);
                        count++;
                        if (count % 10 == 0) log.info("... {}개 파싱 완료", count);
                    }
                    Thread.sleep(50);
                }
            }

            File file = new File("src/main/resources/data/air_purification_plants.json");
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(file, collectedData);
            log.info("✅ 파싱된 JSON 생성 완료: {}", file.getAbsolutePath());

            uploadJsonToS3(file);

        } catch (Exception e) {
            log.error("❌ 공기정화 데이터 추출 실패", e);
        }
    }

    private AirDetailDto fetchDetailAndParse(String idx) {
        try {
            URI detailUri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/selectPuriAirPlantView")
                    .queryParam("serviceKey", apiKey)
                    .queryParam("idx", idx)
                    .build()
                    .toUri();

            String xmlDetailResponse = webClient.get().uri(detailUri).retrieve().bodyToMono(String.class).block();
            AirDetailResponse response = xmlMapper.readValue(xmlDetailResponse, AirDetailResponse.class);

            // ✅ [수정] 이중 구조 반영: getRoot().getResult()
            if (response != null && response.getRoot() != null && response.getRoot().getResult() != null) {
                AirDetailDto dto = response.getRoot().getResult();
                parseHtmlContent(dto);
                return dto;
            }
        } catch (Exception e) {
            log.error("⚠️ 상세 조회 실패 (idx: {})", idx, e);
        }
        return null;
    }

    private void parseHtmlContent(AirDetailDto dto) {
        if (dto.getHtmlContent() == null || dto.getHtmlContent().isEmpty()) return;

        Document doc = Jsoup.parse(dto.getHtmlContent());

        Element imgElement = doc.selectFirst(".img_area01 img");
        if (imgElement != null) dto.setMainImgUrl(imgElement.attr("src"));

        Elements rows = doc.select(".air_table_style01 tr");
        for (Element row : rows) {
            String header = row.select("th").text();
            String value = row.select("td").text();
            if (header.contains("영명")) dto.setEnglishName(value);
            if (header.contains("학명")) dto.setScientificName(value);
        }

        Map<String, String> purification = new HashMap<>();
        Elements effects = doc.select(".air_table_style02 img");
        for (Element eff : effects) {
            String altText = eff.attr("alt");
            if (altText != null && altText.contains(":")) {
                String[] parts = altText.split(":");
                if (parts.length > 1) {
                    purification.put(parts[0].replace("제거량", "").trim(), parts[1].trim());
                }
            }
        }
        dto.setPurificationMap(purification);

        Element placementTitle = doc.selectFirst(".air_tech_tit02");
        if (placementTitle != null) {
            String text = placementTitle.text();
            if (text.contains(":")) {
                dto.setPlacement(text.substring(text.indexOf(":") + 1).trim());
            }
        }

        Elements listItems = doc.select("ul.air_list > li");
        for (Element li : listItems) {
            String text = li.text();
            if (text.startsWith("분류")) dto.setClassification(getValueAfterColon(text));
            else if (text.startsWith("원산지")) dto.setOrigin(getValueAfterColon(text));
            else if (text.startsWith("빛")) dto.setLight(getValueAfterColon(text));
            else if (text.startsWith("온도")) dto.setTemperature(getValueAfterColon(text));
            else if (text.startsWith("용토") || text.startsWith("관수")) dto.setWatering(getValueAfterColon(text));
            else if (text.startsWith("관리")) dto.setManagement(getValueAfterColon(text));
        }
    }

    private String getValueAfterColon(String text) {
        if (text.contains(":")) {
            return text.substring(text.indexOf(":") + 1).trim();
        }
        return text;
    }

    private void uploadJsonToS3(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            String s3Url = s3UploadService.upload(
                    fis,
                    file.getName(),
                    file.length(),
                    "application/json",
                    "airpuriplants"
            );
            log.info("📤 공기정화 JSON S3 업로드 완료 (폴더: plants): {}", s3Url);
        } catch (Exception e) {
            log.error("❌ S3 업로드 실패", e);
        }
    }

    // =================================================================
    // DTO Classes (구조 변경됨: Document -> Root -> Result)
    // =================================================================

    // 1. 목록 조회 (<document><root><result>...</result></root></document>)
    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirListResponse {
        @JsonProperty("root")
        private AirListRoot root;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirListRoot {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JsonProperty("result")
        private List<AirListItem> resultList = new ArrayList<>();
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirListItem {
        private String idx;
        private String title;
    }

    // 2. 상세 조회 (<document><root><result>...</result></root></document>)
    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirDetailResponse {
        @JsonProperty("root")
        private AirDetailRoot root;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirDetailRoot {
        @JsonProperty("result")
        private AirDetailDto result;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirDetailDto {
        private String idx;
        private String title;
        private String content;
        private String htmlContent;
        private String regDate;
        private String publishOrg;

        private String mainImgUrl;
        private String englishName;
        private String scientificName;
        private String placement;
        private Map<String, String> purificationMap;
        private String classification;
        private String origin;
        private String light;
        private String temperature;
        private String watering;
        private String management;
    }
}