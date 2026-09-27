package com.leafy.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.leafy.global.security.jwt.JwtAuthenticationFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 모든 HTTP 요청에 대해 한 줄짜리 액세스 로그를 남기는 필터.
 *
 * <p>AIOps 시스템이 이 앱의 로그를 수집해 장애·침해 원인을 판단하므로,
 * "어느 요청이 어떤 상태코드로 끝났는지"가 로그에 남아야 한다.
 * 특히 스캔성 요청이 4xx로 전부 차단된 상황을 로그만으로 구분할 수 있어야 한다.
 *
 * <p>필터 체인의 가장 앞에 두어, 뒤쪽 필터(Security 등)에서 차단된 요청도
 * 빠짐없이 기록되게 한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogFilter extends OncePerRequestFilter {

    /** 액세스 로그 전용 로거. 애플리케이션 로그와 분리해 수집 계층에서 골라내기 쉽게 한다. */
    private static final org.slf4j.Logger ACCESS_LOG =
            org.slf4j.LoggerFactory.getLogger("ACCESS");

    /** 요청 ID 헤더. nginx 가 $request_id 로 붙여 보내고, 응답에도 그대로 실어 돌려준다. */
    static final String REQUEST_ID_HEADER = "X-Request-ID";

    /** 받아들일 요청 ID 형식. 헤더 값은 클라이언트가 정할 수 있으므로 형식이 맞을 때만 쓴다. */
    private static final java.util.regex.Pattern REQUEST_ID_FORMAT =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final TrustedProxies trustedProxies;

    public AccessLogFilter(@org.springframework.beans.factory.annotation.Value("${app.logging.trusted-proxies:}")
                           String trustedProxies) {
        this.trustedProxies = TrustedProxies.parse(trustedProxies);
        log.info("[Logging] 신뢰 프록시(TRUSTED_PROXIES): {}", this.trustedProxies);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long startNanos = System.nanoTime();

        // 요청 처리 중 발생하는 다른 로그(예외 로그 등)에도 같은 맥락이 붙도록 MDC에 심어둔다.
        MDC.put("http_method", request.getMethod());
        MDC.put("uri", request.getRequestURI());
        MDC.put("source_ip", ClientIpResolver.resolve(request, trustedProxies));

        // nginx 로그와 1:1 로 잇는 키. 요청 처리 중 찍히는 모든 줄에 붙도록 맨 먼저 넣는다.
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put("request_id", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        // 경로 디코딩본. 경로는 항상 있으므로 디코딩할 게 없으면 uri 와 같은 값이 된다.
        MDC.put("uri_decoded", LogSafe.sanitizeQuery(PathDecoder.decode(request.getRequestURI())));

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int status = response.getStatus();
            String rawQuery = request.getQueryString();

            // 상태코드는 필드로 남긴다. 탐지 룰이 4xx/5xx 분포를 세려면
            // 텍스트가 아니라 숫자 필드로 읽을 수 있어야 한다.
            MDC.put("status", String.valueOf(status));
            MDC.put("duration_ms", String.valueOf(durationMs));
            MDC.put("query", LogSafe.sanitizeQuery(rawQuery));
            MDC.put("query_decoded", LogSafe.sanitizeQuery(QueryDecoder.decode(rawQuery)));
            MDC.put("user_agent", LogSafe.sanitize(request.getHeader("User-Agent")));
            MDC.put("principal", resolvePrincipal(request));

            try {
                // 텍스트 포맷일 때도 필드를 읽을 수 있도록 key=value 로 남긴다.
                // MDC 값은 텍스트 패턴에 자동으로 찍히지 않으므로, 메시지 본문에 직접 포함해야
                // JSON 전환 전에도 수집 계층이 값을 파싱할 수 있다.
                ACCESS_LOG.info(
                        "method={} uri={} status={} duration_ms={} source_ip={} principal={} "
                                + "user_agent=\"{}\" query=\"{}\" query_decoded=\"{}\" "
                                // 새 키는 기존 키 뒤에만 붙인다. 앞에 넣으면 기존 파싱이 깨진다.
                                + "request_id={} uri_decoded=\"{}\"",
                        request.getMethod(),
                        MDC.get("uri"),
                        status,
                        durationMs,
                        MDC.get("source_ip"),
                        MDC.get("principal"),
                        MDC.get("user_agent"),
                        MDC.get("query"),
                        MDC.get("query_decoded"),
                        MDC.get("request_id"),
                        MDC.get("uri_decoded"));
            } finally {
                MDC.clear();
            }
        }
    }

    /** 들어온 요청 ID 가 형식에 맞으면 쓰고, 없거나 형식이 틀리면 새로 만든다. */
    static String resolveRequestId(String header) {
        if (header != null && REQUEST_ID_FORMAT.matcher(header).matches()) {
            return header;
        }
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 인증 주체. 인증되지 않은 요청은 anonymous 로 남긴다.
     *
     * <p>이 필터는 체인 가장 바깥에 있어, 로그를 찍는 시점엔 Spring Security가 이미
     * SecurityContextHolder 를 비운 뒤다. 그래서 인증 필터가 요청 속성에 남겨둔 값을
     * 먼저 확인하고, 없을 때만 SecurityContextHolder 를 본다.
     */
    private String resolvePrincipal(HttpServletRequest request) {
        Object fromRequest = request.getAttribute(JwtAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (fromRequest instanceof String name && !name.isBlank()) {
            return LogSafe.sanitize(name);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "anonymous";
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return "anonymous";
        }
        return LogSafe.sanitize(name);
    }
}
