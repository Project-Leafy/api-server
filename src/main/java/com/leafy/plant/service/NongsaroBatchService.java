package com.leafy.plant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.leafy.plant.domain.NongSaro;
import com.leafy.plant.repository.NongSaroRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NongsaroBatchService {

    private final NongSaroRepository nongSaroRepository;
    private final WebClient webClient;
    private final XmlMapper xmlMapper = new XmlMapper(); // XML 수동 파싱을 위한 도구

    @Value("${nongsaro.api-key}")
    private String apiKey;

    @Value("${nongsaro.api.base-url}")
    private String baseUrl;

    @Value("${nongsaro.api.detail-url}")
    private String detailUrl;

    @Value("${nongsaro.api.image-base-url}")
    private String imageBaseUrl;

    /**
     * 농사로 API 데이터를 수집하여 DB에 저장합니다.
     */
    public void fetchAllAndSave() {
        log.info("🚀 농사로 전체 데이터 수집 시작...");

        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("apiKey", apiKey)
                .queryParam("numOfRows", "300")
                .queryParam("pageNo", "1")
                .build()
                .toUri();

        try {
            // [수정] String으로 원본 XML을 받음
            String xmlResponse = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // [수정] XmlMapper로 자바 객체 변환
            NongsaroListResponse listResponse = xmlMapper.readValue(xmlResponse, NongsaroListResponse.class);

            if (isValidResponse(listResponse)) {
                List<NongsaroListItem> items = listResponse.getBody().getItems().getItemList();
                log.info("총 {}개의 데이터를 처리합니다.", items.size());

                for (NongsaroListItem item : items) {
                    fetchDetailAndSave(item);
                }
            }
        } catch (Exception e) {
            log.error("❌ 목록 조회 중 오류 발생", e);
        }
    }

    private boolean isValidResponse(NongsaroListResponse response) {
        return response != null && response.getBody() != null &&
                response.getBody().getItems() != null &&
                response.getBody().getItems().getItemList() != null;
    }

    private void fetchDetailAndSave(NongsaroListItem listItem) {
        String cntntsNo = listItem.getCntntsNo();
        try {
            URI detailUri = UriComponentsBuilder.fromHttpUrl(DETAIL_URL)
                    .queryParam("apiKey", apiKey)
                    .queryParam("cntntsNo", cntntsNo)
                    .build()
                    .toUri();

            // [수정] 상세 조회도 String으로 받아서 수동 파싱
            String xmlDetailResponse = webClient.get()
                    .uri(detailUri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            NongsaroDetailResponse detailResponse = xmlMapper.readValue(xmlDetailResponse, NongsaroDetailResponse.class);

            if (detailResponse != null && detailResponse.getBody() != null && detailResponse.getBody().getItem() != null) {
                saveNongSaro(listItem, detailResponse.getBody().getItem());
            }

        } catch (Exception e) {
            log.error("❌ 상세 조회 실패 (cntntsNo: {})", cntntsNo, e);
        }
    }

    private void saveNongSaro(NongsaroListItem listItem, NongsaroDetailItem detail) {
        Optional<NongSaro> existingData = nongSaroRepository.findByCntntsNo(listItem.getCntntsNo());

        NongSaro.NongSaroBuilder builder = existingData.map(NongSaro::toBuilder)
                .orElse(NongSaro.builder().cntntsNo(listItem.getCntntsNo()));

        // 3. 이미지 URL 생성 로직
        String fullImageUrl = null;
        if (listItem.getRtnFileCours() != null && listItem.getRtnStreFileNm() != null) {
            String path = listItem.getRtnFileCours().split("\\|")[0];
            String fileName = listItem.getRtnStreFileNm().split("\\|")[0];
            fullImageUrl = IMAGE_BASE_URL + path + "/" + fileName;
        }

        NongSaro nongSaro = builder
                // 1. 기본 정보
                .cntntsSj(detail.getCntntsSj()) // 식물명
                .plntbneNm(detail.getPlntbneNm()) //학명
                .plntzrNm(detail.getPlntzrNm()) // 영명
                .distbNm(detail.getDistbNm()) // 유통명
                .fmlNm(detail.getFmlNm()) // 과명 (코드)
                .fmlCodeNm(detail.getFmlCodeNm()) // 과명 (코드명)
                .orgplceInfo(detail.getOrgplceInfo()) // 원산지 정보
                .adviseInfo(detail.getAdviseInfo()) // 조언 정보
                .imageEvlLinkCours(detail.getImageEvlLinkCours())

                // 2. 성장 및 외형 정보
                .growthHgInfo(detail.getGrowthHgInfo())
                .growthAraInfo(detail.getGrowthAraInfo())
                .lefStleInfo(detail.getLefStleInfo())
                .smellCode(detail.getSmellCode())
                .smellCodeNm(detail.getSmellCodeNm())
                .toxctyInfo(detail.getToxctyInfo())
                .prpgtEraInfo(detail.getPrpgtEraInfo())
                .etcEraInfo(detail.getEtcEraInfo())

                // 3. 관리 정보
                .managelevelCode(detail.getManagelevelCode())
                .managelevelCodeNm(detail.getManagelevelCodeNm())
                .grwtveCode(detail.getGrwtveCode())
                .grwtveCodeNm(detail.getGrwtveCodeNm())
                .grwhTpCode(detail.getGrwhTpCode())
                .grwhTpCodeNm(detail.getGrwhTpCodeNm())
                .winterLwetTpCode(detail.getWinterLwetTpCode())
                .winterLwetTpCodeNm(detail.getWinterLwetTpCodeNm())
                .hdCode(detail.getHdCode())
                .hdCodeNm(detail.getHdCodeNm())

                // 4. 토양, 비료, 물주기
                .frtlzrInfo(detail.getFrtlzrInfo())
                .soilInfo(detail.getSoilInfo())
                .watercycleSprngCode(detail.getWatercycleSprngCode())
                .watercycleSprngCodeNm(detail.getWatercycleSprngCodeNm())
                .watercycleSummerCode(detail.getWatercycleSummerCode())
                .watercycleSummerCodeNm(detail.getWatercycleSummerCodeNm())
                .watercycleAutumnCode(detail.getWatercycleAutumnCode())
                .watercycleAutumnCodeNm(detail.getWatercycleAutumnCodeNm())
                .watercycleWinterCode(detail.getWatercycleWinterCode())
                .watercycleWinterCodeNm(detail.getWatercycleWinterCodeNm())

                // 5. 병충해 및 관리 상세
                .dlthtsManageInfo(detail.getDlthtsManageInfo())
                .speclmanageInfo(detail.getSpeclmanageInfo())
                .fncltyInfo(detail.getFncltyInfo())

                // 6. 규격 정보
                .flpodmtBigInfo(detail.getFlpodmtBigInfo())
                .flpodmtMddlInfo(detail.getFlpodmtMddlInfo())
                .flpodmtSmallInfo(detail.getFlpodmtSmallInfo())
                .widthBigInfo(detail.getWidthBigInfo())
                .widthMddlInfo(detail.getWidthMddlInfo())
                .widthSmallInfo(detail.getWidthSmallInfo())
                .vrticlBigInfo(detail.getVrticlBigInfo())
                .vrticlMddlInfo(detail.getVrticlMddlInfo())
                .vrticlSmallInfo(detail.getVrticlSmallInfo())
                .volmeBigInfo(detail.getVolmeBigInfo())
                .volmeMddlInfo(detail.getVolmeMddlInfo())
                .volmeSmallInfo(detail.getVolmeSmallInfo())
                .pcBigInfo(detail.getPcBigInfo())
                .pcMddlInfo(detail.getPcMddlInfo())
                .pcSmallInfo(detail.getPcSmallInfo())

                // 7. 코드 상세 정보 (필드명 재확인!)
                .clCode(detail.getClCode())
                .clCodeNm(detail.getClCodeNm())
                .grwhstleCode(detail.getGrwhstleCode())
                .grwhstleCodeNm(detail.getGrwhstleCodeNm())
                .indoorpsncpacompositionCode(detail.getIndoorpsncpacompositionCode())
                .indoorpsncpacompositionCodeNm(detail.getIndoorpsncpacompositionCodeNm())
                .eclgyCode(detail.getEclgyCode())
                .eclgyCodeNm(detail.getEclgyCodeNm())
                .lefmrkCode(detail.getLefmrkCode())
                .lefmrkCodeNm(detail.getLefmrkCodeNm())
                .lefcolrCode(detail.getLefcolrCode())
                .lefcolrCodeNm(detail.getLefcolrCodeNm())
                .ignSeasonCode(detail.getIgnSeasonCode())
                .ignSeasonCodeNm(detail.getIgnSeasonCodeNm())
                .flclrCode(detail.getFlclrCode())
                .flclrCodeNm(detail.getFlclrCodeNm())
                .fmldeSeasonCode(detail.getFmldeSeasonCode())
                .fmldeSeasonCodeNm(detail.getFmldeSeasonCodeNm())
                .fmldecolrCode(detail.getFmldecolrCode())
                .fmldecolrCodeNm(detail.getFmldecolrCodeNm())
                .prpgtmthCode(detail.getPrpgtmthCode())
                .prpgtmthCodeNm(detail.getPrpgtmthCodeNm())
                .lighttdemanddoCode(detail.getLighttdemanddoCode())
                .lighttdemanddoCodeNm(detail.getLighttdemanddoCodeNm())
                .postngplaceCode(detail.getPostngplaceCode())
                .postngplaceCodeNm(detail.getPostngplaceCodeNm())
                .dlthtsCode(detail.getDlthtsCode())
                .dlthtsCodeNm(detail.getDlthtsCodeNm())
                .managedemanddoCode(detail.getManagedemanddoCode())
                .managedemanddoCodeNm(detail.getManagedemanddoCodeNm())

                // 8. 이미지 정보 (원본 변수 + 생성된 URL)
                .rtnFileCours(listItem.getRtnFileCours())
                .rtnStreFileNm(listItem.getRtnStreFileNm())
                .rtnImageDc(listItem.getRtnImageDc())
                .rtnThumbFileNm(listItem.getRtnThumbFileNm())
                .mainImgUrl(fullImageUrl) // 생성된 URL 저장
                .build();

        nongSaroRepository.save(nongSaro);
    }

    // --- DTO Classes ---

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NongsaroListItem {
        private String cntntsNo;
        private String cntntsSj;
        private String rtnFileCours;
        private String rtnStreFileNm;
        private String rtnImageDc;
        private String rtnThumbFileNm;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NongsaroDetailResponse {
        private Body body;
        @Data @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Body { private NongsaroDetailItem item; }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
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

        // 성장 정보
        private String growthHgInfo;
        private String growthAraInfo;
        private String lefStleInfo;
        private String smellCode;
        private String smellCodeNm;
        private String toxctyInfo;
        private String prpgtEraInfo;
        private String etcEraInfo;

        // 코드 정보
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

        // 관리 상세
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
        private String dlthtsManageInfo;
        private String speclmanageInfo;
        private String fncltyInfo;

        // 규격 정보
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

        // 상세 코드 정보
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