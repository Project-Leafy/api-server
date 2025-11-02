package com.leafy.global.security.oauth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "OAuth2", description = "OAuth2 소셜 로그인")
@RestController
public class OAuth2Controller {

    @Operation(summary = "카카오 로그인", description = "카카오 로그인 페이지로 리디렉션합니다.")
    // 1. 경로 변경: .../code/kakao (콜백 주소)에서 다른 주소로 변경
    @GetMapping("/login/kakao") // 예: /login/kakao 또는 /start/kakao 등
    public void kakaoLogin(HttpServletResponse response) throws IOException {
        // 2. 이 부분은 사용자가 카카오 로그인 버튼을 눌렀을 때 호출되어야 함
        response.sendRedirect("/oauth2/authorization/kakao");
    }

    @Operation(summary = "카카오 로그인 콜백", description = "카카오 로그인 성공 후 호출되는 콜백입니다.")
    @GetMapping("/login/oauth2/code/kakao/callback")
    public String kakaoCallback(@RequestParam String token) {
        // 3. 이 부분은 OAuth2AuthenticationSuccessHandler가 리디렉션하는 최종 목적지 (정상 작동)
        return "JWT Token: " + token;
    }
}
