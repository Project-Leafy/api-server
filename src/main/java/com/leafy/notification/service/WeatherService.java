package com.leafy.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.global.util.KmaGridConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    // .env 파일에 OPEN_WEATHER_API_KEY로 저장된 키를 가져옵니다 (기상청 Encoding Key 사용 권장)
    @Value("${OPEN_WEATHER_API_KEY}")
    private String serviceKey;

    @Value("${kma.api.url}") // application.yaml에서 KMA API URL 주입
    private String kmaApiUrl;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 현재 비가 오는지 확인 (True: 비 옴 / False: 맑음)
     */
    public boolean isRainingNow(Double lat, Double lon) {
        if (lat == null || lon == null) {
            log.warn("[Weather] 위도 또는 경도값이 없어 날씨를 확인할 수 없습니다.");
            return false;
        }

        try {
            // 1. 좌표 변환
            KmaGridConverter.GridCoordinate grid = KmaGridConverter.convertToGrid(lat, lon);
            log.info("[Weather] 사용자 좌표 변환: (lat:{}, lon:{}) -> (nx:{}, ny:{})", lat, lon, grid.getX(), grid.getY());

            // 2. API 호출 파라미터 설정 (현재 날짜, 가장 최근 실황 시간)
            // 초단기실황은 매시 40분마다 생성되므로, 현재 시간 기준 가장 최신 데이터를 조회합니다.
            LocalDate nowDate = LocalDate.now();
            LocalTime nowTime = LocalTime.now();
            // 40분 이전이면 이전 시간대의 데이터를 요청
            if (nowTime.getMinute() <= 40) {
                nowTime = nowTime.minusHours(1);
            }
            String baseDate = nowDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String baseTime = nowTime.format(DateTimeFormatter.ofPattern("HH00"));
            log.debug("[Weather] API 요청 시간 설정: base_date={}, base_time={}", baseDate, baseTime);

            // 3. 인코딩 문제 해결을 위한 URI Factory 설정
            DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(kmaApiUrl);
            factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

            WebClient webClient = webClientBuilder
                    .uriBuilderFactory(factory)
                    .baseUrl(kmaApiUrl)
                    .build();

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", "1")
                            .queryParam("numOfRows", "10") // 실황은 데이터 종류가 적음
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", baseDate)
                            .queryParam("base_time", baseTime)
                            .queryParam("nx", grid.getX())
                            .queryParam("ny", grid.getY())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("[Weather] API 응답 수신: {}", response);

            // 4. 결과 파싱 (PTY: 강수형태)
            return parseIsRaining(response);

        } catch (Exception e) {
            log.error("[Weather] 기상청 API 호출 실패 (기본값 '맑음'으로 처리): {}", e.getMessage());
            return false;
        }
    }

    private boolean parseIsRaining(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode items = root.path("response").path("body").path("items").path("item");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    String category = item.path("category").asText();
                    // [수정] 실황 데이터는 'fcstValue'가 아닌 'obsrValue' 사용
                    String obsrValue = item.path("obsrValue").asText();

                    // PTY: 강수형태 (0:없음, 1:비, 2:비/눈, 3:눈, 4:소나기, 5:빗방울, 6:빗방울/눈날림, 7:눈날림)
                    if ("PTY".equals(category)) {
                        log.info("[Weather] 강수형태(PTY) 확인: {}", obsrValue);
                        // 0이 아니면 어떤 형태든 강수가 있는 것
                        if (!"0".equals(obsrValue)) {
                            log.info("[Weather] 결과: 비 또는 눈이 오는 것으로 판단됩니다.");
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Weather] 날씨 데이터 파싱 실패", e);
        }
        log.info("[Weather] 결과: 강수 없음(맑음)으로 판단됩니다.");
        return false;
    }
}