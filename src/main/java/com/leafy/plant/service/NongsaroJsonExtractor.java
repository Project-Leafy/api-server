package com.leafy.plant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.leafy.plant.dto.NongSaroDto; // ✅ 작성하신 DTO 사용
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import com.leafy.global.storage.FileStorageService;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NongsaroJsonExtractor {

    private final WebClient webClient;
    private final XmlMapper xmlMapper = new XmlMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FileStorageService fileStorageService;

    @Value("${nongsaro.api-key}")
    private String apiKey;

    @Value("${nongsaro.api.base-url}")
    private String baseUrl;

    @Value("${nongsaro.api.detail-url}")
    private String detailUrl;

    @Value("${nongsaro.api.image-base-url}")
    private String imageBaseUrl;

    /**
     * 농사로 API 전체 데이터를 긁어서 JSON 파일로 저장
     */
    public void extractDataToFile() {
        log.info("🚀 농사로 데이터 전체 추출 및 JSON 생성 시작...");
        List<NongSaroDto> collectedPlants = new ArrayList<>();

        // 1. 목록 조회 (전체 가져오기 위해 numOfRows를 크게 설정)
        // 테스트용으로 적게 설정했다가, 실제 돌릴 때는 200~300으로 늘리거나 페이징 루프를 돌리세요.
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("apiKey", apiKey)
                .queryParam("numOfRows", "300")
                .queryParam("pageNo", "1")
                .build()
                .toUri();

        try {
            String xmlResponse = webClient.get().uri(uri).retrieve().bodyToMono(String.class).block();
            NongsaroListResponse listResponse = xmlMapper.readValue(xmlResponse, NongsaroListResponse.class);

            if (isValidResponse(listResponse)) {
                List<NongsaroListItem> items = listResponse.getBody().getItems().getItemList();
                log.info("📋 목록 조회 성공: 총 {}건 발견. 상세 조회 시작...", items.size());

                int count = 0;
                for (NongsaroListItem item : items) {
                    // 2. 상세 조회 및 DTO 변환
                    NongSaroDto plantDto = fetchDetailAndConvert(item);
                    if (plantDto != null) {
                        collectedPlants.add(plantDto);
                        count++;
                        if (count % 10 == 0) log.info("... {}개 처리 완료", count);
                    }
                    Thread.sleep(50); // API 서버 부하 방지
                }

                // 3. 파일 저장
                saveListToJsonFile(collectedPlants);
            }

        } catch (Exception e) {
            log.error("❌ 데이터 추출 중 오류 발생", e);
        }
    }

    private NongSaroDto fetchDetailAndConvert(NongsaroListItem listItem) {
        String cntntsNo = listItem.getCntntsNo();
        try {
            URI detailUri = UriComponentsBuilder.fromHttpUrl(detailUrl)
                    .queryParam("apiKey", apiKey)
                    .queryParam("cntntsNo", cntntsNo)
                    .build()
                    .toUri();

            String xmlDetailResponse = webClient.get().uri(detailUri).retrieve().bodyToMono(String.class).block();
            NongsaroDetailResponse detailResponse = xmlMapper.readValue(xmlDetailResponse, NongsaroDetailResponse.class);

            if (detailResponse != null && detailResponse.getBody() != null && detailResponse.getBody().getItem() != null) {
                NongsaroDetailItem detail = detailResponse.getBody().getItem();
                return mapToDto(listItem, detail);
            }
        } catch (Exception e) {
            log.error("⚠️ 상세 조회 실패 (번호: {}) - 건너뜁니다.", cntntsNo);
        }
        return null;
    }

    private NongSaroDto mapToDto(NongsaroListItem listItem, NongsaroDetailItem d) {
        // 1. 농사로 원본 이미지 URL 생성
        String originalImageUrl = null;
        String fileName = "temp.jpg"; // 기본값

        if (listItem.getRtnFileCours() != null && listItem.getRtnStreFileNm() != null) {
            // 경로: cms_contents/301/
            String path = listItem.getRtnFileCours().split("\\|")[0];
            // 파일명: 12345_MF_ATTACH_01.jpg
            fileName = listItem.getRtnStreFileNm().split("\\|")[0];

            // 1. path 앞뒤 슬래시 정리
            if (path.startsWith("/")) path = path.substring(1);
            if (!path.endsWith("/")) path = path + "/";

            // 2. 도메인 직접 연결 (설정값 imageBaseUrl 무시) -> 중복 원천 차단
            // 농사로 이미지는 항상 이 도메인 아래에 있습니다.
            originalImageUrl = "http://www.nongsaro.go.kr/" + path + fileName;
        }

        // 2. 이미지를 S3로 업로드하고, 그 URL로 바꿔치기
        String finalImageUrl = null;
        if (originalImageUrl != null && !originalImageUrl.isBlank()) {
            try {
                finalImageUrl = uploadImageToS3(originalImageUrl, fileName);
                log.info("📸 이미지 변환 성공: {} -> {}", fileName, finalImageUrl);
            } catch (Exception e) {
                log.warn("⚠️ 이미지 업로드 실패 (원본 사용): {}", originalImageUrl);
                // 실패하면 원본이라도 넣거나, null로 처리
                finalImageUrl = null;
            }
        }

        // ✅ 모든 필드 매핑 (NongSaroDto 빌더 사용)
        return NongSaroDto.builder()
                // 1. 기본 정보
                .cntntsNo(d.getCntntsNo())
                .cntntsSj(d.getCntntsSj())
                .plntbneNm(d.getPlntbneNm())
                .plntzrNm(d.getPlntzrNm())
                .distbNm(d.getDistbNm())
                .fmlNm(d.getFmlNm())
                .fmlCodeNm(d.getFmlCodeNm())
                .orgplceInfo(d.getOrgplceInfo())
                .adviseInfo(d.getAdviseInfo())
                .imageEvlLinkCours(d.getImageEvlLinkCours())

                // 2. 성장 및 외형
                .growthHgInfo(d.getGrowthHgInfo())
                .growthAraInfo(d.getGrowthAraInfo())
                .lefStleInfo(d.getLefStleInfo())
                .smellCode(d.getSmellCode())
                .smellCodeNm(d.getSmellCodeNm())
                .toxctyInfo(d.getToxctyInfo())
                .prpgtEraInfo(d.getPrpgtEraInfo())
                .etcEraInfo(d.getEtcEraInfo())

                // 3. 관리 정보
                .managelevelCode(d.getManagelevelCode())
                .managelevelCodeNm(d.getManagelevelCodeNm())
                .grwtveCode(d.getGrwtveCode())
                .grwtveCodeNm(d.getGrwtveCodeNm())
                .grwhTpCode(d.getGrwhTpCode())
                .grwhTpCodeNm(d.getGrwhTpCodeNm())
                .winterLwetTpCode(d.getWinterLwetTpCode())
                .winterLwetTpCodeNm(d.getWinterLwetTpCodeNm())
                .hdCode(d.getHdCode())
                .hdCodeNm(d.getHdCodeNm())

                // 4. 토양, 비료, 물주기
                .frtlzrInfo(d.getFrtlzrInfo())
                .soilInfo(d.getSoilInfo())
                .watercycleSprngCode(d.getWatercycleSprngCode())
                .watercycleSprngCodeNm(d.getWatercycleSprngCodeNm())
                .watercycleSummerCode(d.getWatercycleSummerCode())
                .watercycleSummerCodeNm(d.getWatercycleSummerCodeNm())
                .watercycleAutumnCode(d.getWatercycleAutumnCode())
                .watercycleAutumnCodeNm(d.getWatercycleAutumnCodeNm())
                .watercycleWinterCode(d.getWatercycleWinterCode())
                .watercycleWinterCodeNm(d.getWatercycleWinterCodeNm())

                // 5. 병충해 및 관리 상세
                .dlthtsManageInfo(d.getDlthtsManageInfo())
                .speclmanageInfo(d.getSpeclmanageInfo())
                .fncltyInfo(d.getFncltyInfo())

                // 6. 규격 정보 (화분, 폭, 높이, 볼륨, 가격)
                .flpodmtBigInfo(d.getFlpodmtBigInfo())
                .flpodmtMddlInfo(d.getFlpodmtMddlInfo())
                .flpodmtSmallInfo(d.getFlpodmtSmallInfo())
                .widthBigInfo(d.getWidthBigInfo())
                .widthMddlInfo(d.getWidthMddlInfo())
                .widthSmallInfo(d.getWidthSmallInfo())
                .vrticlBigInfo(d.getVrticlBigInfo())
                .vrticlMddlInfo(d.getVrticlMddlInfo())
                .vrticlSmallInfo(d.getVrticlSmallInfo())
                .volmeBigInfo(d.getVolmeBigInfo())
                .volmeMddlInfo(d.getVolmeMddlInfo())
                .volmeSmallInfo(d.getVolmeSmallInfo())
                .pcBigInfo(d.getPcBigInfo())
                .pcMddlInfo(d.getPcMddlInfo())
                .pcSmallInfo(d.getPcSmallInfo())

                // 7. 코드 상세 정보
                .clCode(d.getClCode())
                .clCodeNm(d.getClCodeNm())
                .grwhstleCode(d.getGrwhstleCode())
                .grwhstleCodeNm(d.getGrwhstleCodeNm())
                .indoorpsncpacompositionCode(d.getIndoorpsncpacompositionCode())
                .indoorpsncpacompositionCodeNm(d.getIndoorpsncpacompositionCodeNm())
                .eclgyCode(d.getEclgyCode())
                .eclgyCodeNm(d.getEclgyCodeNm())
                .lefmrkCode(d.getLefmrkCode())
                .lefmrkCodeNm(d.getLefmrkCodeNm())
                .lefcolrCode(d.getLefcolrCode())
                .lefcolrCodeNm(d.getLefcolrCodeNm())
                .ignSeasonCode(d.getIgnSeasonCode())
                .ignSeasonCodeNm(d.getIgnSeasonCodeNm())
                .flclrCode(d.getFlclrCode())
                .flclrCodeNm(d.getFlclrCodeNm())
                .fmldeSeasonCode(d.getFmldeSeasonCode())
                .fmldeSeasonCodeNm(d.getFmldeSeasonCodeNm())
                .fmldecolrCode(d.getFmldecolrCode())
                .fmldecolrCodeNm(d.getFmldecolrCodeNm())
                .prpgtmthCode(d.getPrpgtmthCode())
                .prpgtmthCodeNm(d.getPrpgtmthCodeNm())
                .lighttdemanddoCode(d.getLighttdemanddoCode())
                .lighttdemanddoCodeNm(d.getLighttdemanddoCodeNm())
                .postngplaceCode(d.getPostngplaceCode())
                .postngplaceCodeNm(d.getPostngplaceCodeNm())
                .dlthtsCode(d.getDlthtsCode())
                .dlthtsCodeNm(d.getDlthtsCodeNm())
                .managedemanddoCode(d.getManagedemanddoCode())
                .managedemanddoCodeNm(d.getManagedemanddoCodeNm())

                // 8. 이미지 정보
                .rtnFileCours(listItem.getRtnFileCours())
                .rtnStreFileNm(listItem.getRtnStreFileNm())
                .rtnImageDc(listItem.getRtnImageDc())
                .rtnThumbFileNm(listItem.getRtnThumbFileNm())
                .mainImgUrl(finalImageUrl)// 조합된 URL
                .build();
    }

    private void saveListToJsonFile(List<NongSaroDto> plants) {
        try {
            File file = new File("src/main/resources/data/nongsaro_plants.json");
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(file, plants);
            log.info("✅ JSON 파일 생성 완료! 경로: {}", file.getAbsolutePath());
            log.info("✅ 총 저장된 식물 수: {}", plants.size());
        } catch (Exception e) {
            log.error("❌ JSON 파일 저장 실패", e);
        }
    }

    private boolean isValidResponse(NongsaroListResponse response) {
        return response != null && response.getBody() != null &&
                response.getBody().getItems() != null &&
                response.getBody().getItems().getItemList() != null;
    }

    /**
     * 농사로 이미지를 다운로드해서 S3에 올리는 헬퍼 메서드
     */
    private String uploadImageToS3(String imageUrl, String originalFileName) throws Exception {
        URL url = new URL(imageUrl);
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        long contentLength = connection.getContentLengthLong();
        String contentType = connection.getContentType();
        if (contentType == null) contentType = "image/jpeg";

        try (InputStream inputStream = connection.getInputStream()) {
            return fileStorageService.upload(inputStream, originalFileName, contentLength, contentType, "nongsaro_img");
        } catch (Exception e) {
            // 👇 에러가 났을 때 구체적인 이유(403, 404 등)를 알기 위해 로그를 강화함
            log.error("❌ 이미지 다운로드 실패 URL: {} / 사유: {}", imageUrl, e.toString());
            throw e; // 상위 메서드로 예외를 던져서 원본 URL을 사용하게 함
        }
    }

    // =================================================================
    // XML Parsing용 Inner Classes (DTO와 필드명 일치 + String 타입)
    // =================================================================

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NongsaroListResponse {
        private Body body;
        @Data @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Body { private Items items; }
        @Data @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Items {
            @JacksonXmlElementWrapper(useWrapping = false)
            @JsonProperty("item")
            private List<NongsaroListItem> itemList;
        }
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NongsaroListItem {
        private String cntntsNo;
        private String cntntsSj;
        private String rtnFileCours;
        private String rtnStreFileNm;
        private String rtnImageDc;
        private String rtnThumbFileNm;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NongsaroDetailResponse {
        private Body body;
        @Data @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Body { private NongsaroDetailItem item; }
    }

    /**
     * XML 데이터를 받아올 임시 그릇 (모든 필드 String 처리)
     * NongSaroDto와 필드명이 정확히 일치해야 자동 매핑됨
     */
    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NongsaroDetailItem {
        // 기본 정보
        private String cntntsNo;
        private String cntntsSj;
        private String plntbneNm;
        private String plntzrNm;
        private String distbNm;
        private String fmlNm;
        private String fmlCodeNm;
        private String orgplceInfo;
        private String adviseInfo;
        private String imageEvlLinkCours;

        // 성장 및 외형
        private String growthHgInfo;
        private String growthAraInfo;
        private String lefStleInfo;
        private String smellCode;
        private String smellCodeNm;
        private String toxctyInfo;
        private String prpgtEraInfo;
        private String etcEraInfo;

        // 관리 정보
        private String managelevelCode;
        private String managelevelCodeNm;
        private String grwtveCode;
        private String grwtveCodeNm;
        private String grwhTpCode;
        private String grwhTpCodeNm;
        private String winterLwetTpCode;
        private String winterLwetTpCodeNm;
        private String hdCode;
        private String hdCodeNm;

        // 토양, 비료, 물주기
        private String frtlzrInfo;
        private String soilInfo;
        private String watercycleSprngCode;
        private String watercycleSprngCodeNm;
        private String watercycleSummerCode;
        private String watercycleSummerCodeNm;
        private String watercycleAutumnCode;
        private String watercycleAutumnCodeNm;
        private String watercycleWinterCode;
        private String watercycleWinterCodeNm;

        // 병충해 및 관리 상세
        private String dlthtsManageInfo;
        private String speclmanageInfo;
        private String fncltyInfo;

        // 규격 정보 (화분, 폭, 높이, 볼륨, 가격)
        private String flpodmtBigInfo;
        private String flpodmtMddlInfo;
        private String flpodmtSmallInfo;
        private String widthBigInfo;
        private String widthMddlInfo;
        private String widthSmallInfo;
        private String vrticlBigInfo;
        private String vrticlMddlInfo;
        private String vrticlSmallInfo;
        private String volmeBigInfo;
        private String volmeMddlInfo;
        private String volmeSmallInfo;
        private String pcBigInfo;
        private String pcMddlInfo;
        private String pcSmallInfo;

        // 코드 상세 정보
        private String clCode;
        private String clCodeNm;
        private String grwhstleCode;
        private String grwhstleCodeNm;
        private String indoorpsncpacompositionCode;
        private String indoorpsncpacompositionCodeNm;
        private String eclgyCode;
        private String eclgyCodeNm;
        private String lefmrkCode;
        private String lefmrkCodeNm;
        private String lefcolrCode;
        private String lefcolrCodeNm;
        private String ignSeasonCode;
        private String ignSeasonCodeNm;
        private String flclrCode;
        private String flclrCodeNm;
        private String fmldeSeasonCode;
        private String fmldeSeasonCodeNm;
        private String fmldecolrCode;
        private String fmldecolrCodeNm;
        private String prpgtmthCode;
        private String prpgtmthCodeNm;
        private String lighttdemanddoCode;
        private String lighttdemanddoCodeNm;
        private String postngplaceCode;
        private String postngplaceCodeNm;
        private String dlthtsCode;
        private String dlthtsCodeNm;
        private String managedemanddoCode;
        private String managedemanddoCodeNm;
    }
}