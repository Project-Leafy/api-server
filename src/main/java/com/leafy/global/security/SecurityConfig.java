package com.leafy.global.security;

import com.leafy.global.security.jwt.JwtAuthenticationFilter;
import com.leafy.global.security.jwt.JwtTokenProvider;
import com.leafy.global.security.oauth.CustomOAuth2UserService;
import com.leafy.global.security.oauth.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    /**
     * Spring Security 필터 체인을 구성합니다.
     *
     * @param http HttpSecurity 객체
     * @return SecurityFilterChain 객체
     * @throws Exception 예외
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 보호를 비활성화합니다.
                .csrf(csrf -> csrf.disable())
                // 세션을 사용하지 않으므로 STATELESS로 설정합니다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 요청에 대한 인가를 설정합니다.
                .authorizeHttpRequests(auth -> auth
                        // 특정 경로에 대한 요청은 모두 허용합니다.
                        .requestMatchers(
                                "/login/kakao", // ✅ 로그인 시작 경로를 /login/kakao로 수정 (Controller와 일치)
                                "/login/oauth2/code/kakao/callback",
                                "/token/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**"
                        ).permitAll()
                        // 그 외의 모든 요청은 인증이 필요합니다.
                        .anyRequest().authenticated()
                )
                // OAuth2 로그인을 설정합니다.
                .oauth2Login(oauth2 -> oauth2
                        // 사용자 정보 엔드포인트를 설정합니다.
                        .userInfoEndpoint(userInfo -> userInfo
                                // 커스텀 OAuth2 사용자 서비스를 사용합니다.
                                .userService(customOAuth2UserService)
                        )
                        // 인증 성공 핸들러를 설정합니다.
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                )
                // JWT 인증 필터를 추가합니다.
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
