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

    /**
     * 신뢰 프록시 설정을 반영해 원 소스 IP를 구한다.
     *
     * <ul>
     *   <li>설정이 비어 있으면 기존 동작 그대로(헤더를 무조건 믿고 X-Forwarded-For 맨 앞).</li>
     *   <li>요청을 보낸 쪽(getRemoteAddr)이 신뢰 프록시가 아니면 헤더를 전부 무시한다.</li>
     *   <li>신뢰 프록시면 X-Forwarded-For 를 <b>오른쪽 끝부터</b> 읽어, 신뢰 프록시를 건너뛴 뒤
     *       처음 나오는 주소를 쓴다. 프록시는 뒤에 덧붙이므로 맨 앞은 클라이언트가 정한 값이고,
     *       오른쪽일수록 신뢰 프록시가 직접 적은 값이다.</li>
     * </ul>
     */
    static String resolve(HttpServletRequest request, TrustedProxies trusted) {
        if (trusted.isEmpty()) {
            return resolve(request);
        }
        String remote = request.getRemoteAddr();
        if (remote == null) {
            return "-";
        }
        if (!trusted.contains(remote)) {
            return remote;
        }

        // 신뢰 프록시가 직접 적어 준 값만 믿는다. (프론트 nginx 는 클라이언트가 보낸 CF-Connecting-IP 를 지운다)
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && TrustedProxies.isIpLiteral(cf.trim())) {
            return cf.trim();
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            String closestTrusted = remote;
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (!TrustedProxies.isIpLiteral(hop)) {
                    // IP 가 아닌 값은 신뢰 프록시가 적었을 리 없다. 확인된 가장 가까운 주소를 쓴다.
                    return closestTrusted;
                }
                if (!trusted.contains(hop)) {
                    return hop;
                }
                closestTrusted = hop;
            }
            // 전부 신뢰 프록시면 가장 바깥쪽을 쓴다.
            return closestTrusted;
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && TrustedProxies.isIpLiteral(realIp.trim())) {
            return realIp.trim();
        }
        return remote;
    }

    /** 신뢰 프록시 설정이 없을 때의 기존 동작. */
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
