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

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String KMA_API_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";

    /**
     * 오늘 비가 오는지 확인 (True: 비 옴 / False: 맑음)
     */
    public boolean willItRainToday(Double lat, Double lon) {
        if (lat == null || lon == null) return false;

        try {
            // 1. 좌표 변환
            KmaGridConverter.GridCoordinate grid = KmaGridConverter.convertToGrid(lat, lon);

            // 2. API 호출 파라미터 설정 (오늘 날짜, 아침 05시 기준 예보)
            String baseDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String baseTime = "0500"; // 05시 발표 예보가 정확도가 높음

            // 3. 인코딩 문제 해결을 위한 URI Factory 설정
            DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(KMA_API_URL);
            factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE); // 키가 이미 인코딩된 경우

            WebClient webClient = webClientBuilder
                    .uriBuilderFactory(factory)
                    .baseUrl(KMA_API_URL)
                    .build();

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", "1")
                            .queryParam("numOfRows", "100") // 넉넉하게 조회
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", baseDate)
                            .queryParam("base_time", baseTime)
                            .queryParam("nx", grid.getX())
                            .queryParam("ny", grid.getY())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 4. 결과 파싱 (POP: 강수확률, PTY: 강수형태)
            return parseRainCheck(response);

        } catch (Exception e) {
            log.error("기상청 API 호출 실패 (기본값 False 반환): {}", e.getMessage());
            return false; // 에러 나면 그냥 맑음으로 처리 (안전하게)
        }
    }

    private boolean parseRainCheck(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode items = root.path("response").path("body").path("items").path("item");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    String category = item.path("category").asText();
                    String fcstValue = item.path("fcstValue").asText();

                    // POP: 강수확률 (0~100) -> 60% 이상이면 비 온다고 판단
                    if ("POP".equals(category)) {
                        if (Integer.parseInt(fcstValue) >= 60) return true;
                    }
                    // PTY: 강수형태 (0:없음, 1:비, 2:비/눈, 3:눈, 4:소나기)
                    if ("PTY".equals(category)) {
                        if (!"0".equals(fcstValue)) return true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("날씨 데이터 파싱 실패", e);
        }
        return false;
    }
}