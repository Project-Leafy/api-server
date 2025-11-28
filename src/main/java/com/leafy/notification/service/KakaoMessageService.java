package com.leafy.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoMessageService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper; // JSON 변환용

    // application.properties (또는 .env)에 있는 키 값을 가져옵니다.
    // 변수명이 다르면 수정해주세요! (예: ${spring.security.oauth2.client.registration.kakao.client-id})
    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String clientId;

    private static final String KAKAO_API_BASE_URL = "https://kapi.kakao.com";
    private static final String KAKAO_AUTH_BASE_URL = "https://kauth.kakao.com";

    /**
     * 카카오톡 '나에게 보내기' (기본 텍스트 템플릿)
     * @param user 알림을 보낼 사용자
     * @param messageContent 전송할 메시지 내용
     * @return 성공 여부
     */
    @Transactional
    public boolean sendSelfMessage(User user, String messageContent) {
        if (user.getKakaoAccessToken() == null) {
            log.warn("사용자의 카카오 토큰이 없습니다. UserID: {}", user.getUserId());
            return false;
        }

        try {
            // 1. 메시지 전송 시도
            postMessage(user.getKakaoAccessToken(), messageContent);
            log.info("카카오톡 메시지 전송 성공. UserID: {}", user.getUserId());
            return true;

        } catch (WebClientResponseException.Unauthorized e) {
            // 2. 토큰 만료(401) 시 -> 토큰 갱신 후 재시도
            log.info("카카오 토큰 만료됨. 갱신을 시도합니다. UserID: {}", user.getUserId());

            if (refreshKakaoToken(user)) {
                // 갱신된 토큰으로 재전송
                try {
                    postMessage(user.getKakaoAccessToken(), messageContent);
                    log.info("토큰 갱신 후 메시지 전송 성공. UserID: {}", user.getUserId());
                    return true;
                } catch (Exception ex) {
                    log.error("재전송 실패. UserID: {}", user.getUserId(), ex);
                }
            }
        } catch (Exception e) {
            log.error("카카오 메시지 전송 중 알 수 없는 오류 발생", e);
        }
        return false;
    }

    // 실제 카카오 API 호출 (POST /v2/api/talk/memo/default/send)
    private void postMessage(String accessToken, String text) throws JsonProcessingException {
        // 템플릿 JSON 생성 (Text 타입)
        Map<String, Object> templateObj = new HashMap<>();
        templateObj.put("object_type", "text");
        templateObj.put("text", text);
        templateObj.put("link", Map.of(
                "web_url", "http://localhost:5500", // 클릭 시 이동할 PC 주소
                "mobile_web_url", "http://localhost:5500" // 클릭 시 이동할 모바일 주소
        ));
        templateObj.put("button_title", "Leafy 앱으로 가기");

        String templateJson = objectMapper.writeValueAsString(templateObj);

        // WebClient로 전송
        WebClient.create(KAKAO_API_BASE_URL).post()
                .uri("/v2/api/talk/memo/default/send")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("template_object", templateJson))
                .retrieve()
                .bodyToMono(String.class)
                .block(); // 동기 처리 (스케줄러에서 순차 발송하므로 괜찮음)
    }

    /**
     * 카카오 토큰 갱신 로직
     */
    @Transactional
    public boolean refreshKakaoToken(User user) {
        String refreshToken = user.getKakaoRefreshToken();
        if (refreshToken == null) {
            log.error("리프레시 토큰이 없어 갱신 불가능. UserID: {}", user.getUserId());
            return false;
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "refresh_token");
            formData.add("client_id", clientId);
            formData.add("refresh_token", refreshToken);

            // 토큰 갱신 API 호출
            Map response = WebClient.create(KAKAO_AUTH_BASE_URL).post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("access_token")) {
                String newAccessToken = (String) response.get("access_token");
                // 리프레시 토큰은 갱신될 수도, 안 될 수도 있음 (안 오면 기존 것 유지)
                String newRefreshToken = response.containsKey("refresh_token") ?
                        (String) response.get("refresh_token") : null;

                // DB 업데이트 (더티 체킹)
                user.updateKakaoToken(newAccessToken, newRefreshToken);
                // userRepository.save(user); // Transactional이라 생략 가능하지만 명시 가능

                log.info("카카오 토큰 갱신 완료. UserID: {}", user.getUserId());
                return true;
            }

        } catch (Exception e) {
            log.error("카카오 토큰 갱신 실패. UserID: {}", user.getUserId(), e);
        }
        return false;
    }
}