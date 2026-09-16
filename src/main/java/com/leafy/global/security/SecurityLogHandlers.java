package com.leafy.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Security 가 요청을 거부할 때 로그를 남기는 핸들러들.
 *
 * <p>기본 동작은 401/403 응답만 내보내고 <b>로그를 남기지 않는다.</b>
 * 그래서 "외부 IP에서 인증이 반복 실패하고 있다" 같은 판단의 근거가 앱 로그에 생기지 않는다.
 * 인증 실패와 권한 부족을 구분해 기록해, 자격증명 공격과 권한 상승 시도를 나눠 볼 수 있게 한다.
 */
@Slf4j
@Configuration
public class SecurityLogHandlers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 인증 안 됨 (401). 토큰이 없거나 유효하지 않은 경우. */
    @Bean
    public AuthenticationEntryPoint loggingAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("[BLOCKED] {} {} 인증 실패 (status=401, reason=unauthenticated, detail={})",
                    request.getMethod(), request.getRequestURI(), authException.getMessage());
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated", "인증이 필요합니다.");
        };
    }

    /** 권한 부족 (403). 인증은 됐지만 접근 권한이 없는 경우. */
    @Bean
    public AccessDeniedHandler loggingAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn("[BLOCKED] {} {} 권한 부족 (status=403, reason=access_denied, detail={})",
                    request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());
            write(response, HttpServletResponse.SC_FORBIDDEN, "access_denied", "접근 권한이 없습니다.");
        };
    }

    private void write(HttpServletResponse response, int status, String reason, String message)
            throws java.io.IOException {
        // 이미 응답이 나가기 시작했다면 덮어쓸 수 없다.
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("reason", reason);
        body.put("message", message);

        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
