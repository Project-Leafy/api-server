package com.leafy.global.security.oauth;

import com.leafy.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import com.leafy.global.security.jwt.TokenInfo;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * OAuth2 인증 성공 시 호출됩니다.
     * JWT 토큰을 생성하고 사용자를 리디렉션합니다.
     *
     * @param request        HttpServletRequest
     * @param response       HttpServletResponse
     * @param authentication Authentication 객체
     * @throws IOException      입출력 예외
     * @throws ServletException 서블릿 예외
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);

        // ✨ 프론트엔드 URL로 직접 리다이렉트 (토큰을 Fragment로 전달하여 보안 강화)
        // 실제 프론트엔드 URL로 변경해야 한다. 예: http://localhost:3000
        //String redirectUrl = "http://localhost:8080/oauth/callback#accessToken=" + tokenInfo.getAccessToken();

        //  프론트엔드 URL로 리다이렉트할 경우 (프론트엔드 서버 포트에 맞게 수정)
        String redirectUrl = "http://localhost:5500/callback.html#accessToken=" + tokenInfo.getAccessToken();
        response.sendRedirect(redirectUrl);
    }
}
