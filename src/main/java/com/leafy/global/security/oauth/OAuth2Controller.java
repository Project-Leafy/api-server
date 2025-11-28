package com.leafy.global.security.oauth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "OAuth2", description = "OAuth2 소셜 로그인")
@RestController
public class OAuth2Controller {

    @Operation(summary = "카카오 로그인 시작", description = "카카오 로그인 페이지로 리디렉션합니다.")
    @GetMapping("/login/kakao")
    public void kakaoLogin(HttpServletResponse response) throws IOException {
        // Spring Security의 기본 OAuth2 시작 경로로 리디렉션
        response.sendRedirect("/oauth2/authorization/kakao");
    }

    /**
     * OAuth2 인증 성공 시 최종 리다이렉트되는 경로다.
     * SuccessHandler가 이 경로로 토큰을 Fragment(#)에 담아 리다이렉트한다.
     * 이 엔드포인트는 404 에러와 무한 루프를 방지하기 위해 존재해야 한다.
     *
     * @return 성공 메시지 (실제로는 프론트엔드가 이 페이지에서 토큰을 처리한다)
     */
    @Operation(summary = "OAuth2 로그인 콜백", description = "OAuth2 로그인 성공 후 최종 리다이렉트되는 경로입니다.")
    @GetMapping("/oauth/callback")
    public String oauthCallback() {
        // 프론트엔드는 이 응답을 받는 것이 아니라,
        // 이 페이지로 리다이렉트될 때 URL의 Fragment(#accessToken=...)를 JS로 읽어간다.
        return "Authentication Successful. Token is in the URL fragment.";
    }

    /*
     * /login/oauth2/code/kakao/callback 엔드포인트는
     * SuccessHandler를 사용하면서 더 이상 필요하지 않으므로 제거되었다.
     */
}
