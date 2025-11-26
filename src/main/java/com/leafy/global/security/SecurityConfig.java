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

// CORS 설정을 위한 import 추가
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomOAuth2UserService customOAuth2UserService; // Role을 부여하는 서비스
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler; // JWT 토큰 발행 핸들러

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
                // CSRF 보호를 비활성화합니다. (JWT 사용 시)
                .csrf(csrf -> csrf.disable())

                // CORS 설정 추가 -> 프론트엔드랑 연결되도록
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // API 서버는 폼 로그인을 사용하지 않으므로 명시적으로 비활성화
                .formLogin(form -> form.disable())

                // H2 Console을 위한 Frame 허용 설정
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))

                // 세션을 사용하지 않으므로 STATELESS로 설정합니다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 요청에 대한 인가를 설정합니다.
                .authorizeHttpRequests(auth -> auth
                        // (예시) 향후 관리자 페이지 경로를 여기에 추가할 수 있습니다.
                        // .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 다음 경로들은 인증 없이 허용합니다.
                        .requestMatchers(
                                "/login/kakao", // 카카오 로그인 시작 경로
                                //"/login/oauth2/code/kakao/callback",  // 사용하지 않음 (주석 처리)
                                "/token/**", // 토큰 관련 경로 (예: 리프레시)
                                "/swagger-ui.html", // Swagger UI
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**",
                                "/h2-console/**", //개발용 메모리
                                "http://localhost:8080", //Swagger UI 테스트를 위해 로컬 서버 허용
                                "/home" // ✨ SuccessHandler가 리다이렉트하는 최종 경로
                                // [수정됨] /oauth/callback 제거 (더 이상 백엔드가 호출받지 않음)
                        ).permitAll()
                        // 그 외의 모든 요청은 인증이 필요합니다.
                        .anyRequest().authenticated()
                )
                // OAuth2 로그인을 설정합니다.
                .oauth2Login(oauth2 -> oauth2
                        // 사용자 정보 엔드포인트를 설정합니다.
                        .userInfoEndpoint(userInfo -> userInfo
                                // 커스텀 OAuth2 사용자 서비스를 사용합니다. (Role 부여)
                                .userService(customOAuth2UserService)
                        )
                        // 인증 성공 핸들러를 설정합니다. (JWT 발행 및 리다이렉트)
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                )
                // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 추가합니다.
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    // (추가) CORS 설정 Bean
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 프론트엔드 서버 주소(5500) 허용 -> vsCode Live Server
        /*
        * 모든 HTML 페이지는 프론트엔드 서버(VS Code Live Server, localhost:5500)가 담당한다.
        * */
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5500", "http://127.0.0.1:5500"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*")); // 모든 헤더 허용
        configuration.setAllowCredentials(true); // 자격 증명(쿠키, 인증 헤더) 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 모든 경로에 대해 위 설정 적용
        return source;
    }
}

