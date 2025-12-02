package com.leafy.global.security.oauth;

import com.leafy.global.security.jwt.JwtTokenProvider;
import com.leafy.global.security.jwt.TokenInfo;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    // ✨ [추가] 카카오 토큰 정보가 담긴 'AuthorizedClient'를 가져오기 위한 저장소
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    /**
     * OAuth2 인증 성공 시 호출됩니다.
     * 1. 카카오 Access/Refresh Token을 추출하여 DB에 저장합니다.
     * 2. 자체 JWT 토큰을 생성하고 사용자를 프론트엔드로 리디렉션합니다.
     *
     * @param request        HttpServletRequest
     * @param response       HttpServletResponse
     * @param authentication Authentication 객체
     * @throws IOException      입출력 예외
     * @throws ServletException 서블릿 예외
     */
    @Override
    @Transactional // DB 업데이트를 위해 트랜잭션 처리
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 1. 리프레시 토큰 등 카카오 인증 정보 저장하기 (핵심 로직)
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {

            // Spring Security가 내부적으로 저장해둔 'AuthorizedClient' 로드
            OAuth2AuthorizedClient authorizedClient = authorizedClientRepository.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(),
                    oauthToken,
                    request
            );

            if (authorizedClient != null && authorizedClient.getRefreshToken() != null) {
                String kakaoAccessToken = authorizedClient.getAccessToken().getTokenValue();
                String kakaoRefreshToken = authorizedClient.getRefreshToken().getTokenValue();

                // Principal에서 이메일 추출 (CustomOAuth2User)
                String email = ((CustomOAuth2User) authentication.getPrincipal()).getEmail();

                // DB에서 User 조회 후 토큰 업데이트
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

                // User 엔티티에 추가한 편의 메서드 호출
                user.updateKakaoToken(kakaoAccessToken, kakaoRefreshToken);

                // Dirty Checking으로 자동 저장되지만, 로그로 확인
                log.info("✅ Kakao Tokens saved for user: {}", email);
            } else {
                log.warn("⚠️ AuthorizedClient or RefreshToken is null. Check Kakao Developer settings.");
            }
        }

        // 2. JWT 토큰 생성 및 리다이렉트 (기존 로직 유지)
        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);

        // 디버깅용 로그
        System.out.println("=========================================================");
        System.out.println("Access Token: " + tokenInfo.getAccessToken());
        System.out.println("Refresh Token: " + tokenInfo.getRefreshToken());
        System.out.println("=========================================================");

        // 프론트엔드 URL로 리다이렉트 (포트 번호 확인: 5500)
        //String redirectUrl = "http://localhost:5500/callback.html#accessToken=" + tokenInfo.getAccessToken();
        // 이걸로 바꿔 (포트 80이라 생략 가능)
        String redirectUrl = "http://3.36.110.234/callback.html#accessToken=" + tokenInfo.getAccessToken();
        response.sendRedirect(redirectUrl);
    }
}