package com.leafy.global.logging;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청의 원 소스 IP를 해석한다.
 *
 * <p>앱이 nginx·터널(Tailscale/Cloudflare) 뒤에 놓이면 remote address 는
 * 프록시의 IP가 되어 버린다. "외부 IP에서 인증 50회 실패" 같은 판단은
 * 원 소스 IP가 있어야 성립하므로 X-Forwarded-For 를 우선 해석한다.
 */
final class ClientIpResolver {

    /**
     * 프록시가 붙이는 헤더들. 앞쪽일수록 우선한다.
     * Cloudflare 를 쓰면 CF-Connecting-IP 가 가장 신뢰할 수 있다.
     */
    private static final String[] HEADERS = {
            "CF-Connecting-IP",
            "X-Forwarded-For",
            "X-Real-IP"
    };

    private ClientIpResolver() {
    }

    static String resolve(HttpServletRequest request) {
        for (String header : HEADERS) {
            String value = request.getHeader(header);
            if (value == null || value.isBlank()) {
                continue;
            }
            // X-Forwarded-For 는 "client, proxy1, proxy2" 형태로 누적된다.
            // 맨 앞이 원 클라이언트다.
            int comma = value.indexOf(',');
            String candidate = (comma >= 0 ? value.substring(0, comma) : value).trim();
            if (!candidate.isBlank()) {
                return LogSafe.sanitize(candidate);
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "-" : remote;
    }
}
