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
        response.sendRedirect("/login/oauth2/code/kakao/callback?token=" + tokenInfo.getAccessToken());
    }
}
