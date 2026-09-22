package com.leafy.global.logging;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 백엔드 바로 앞에 있는 신뢰 프록시 목록 (CIDR, 쉼표 구분).
 *
 * <p>여기에 든 주소에서 온 요청만 X-Forwarded-For 등 전달 헤더를 믿는다.
 * <b>도커 게이트웨이(.1)를 넣으면 안 된다.</b> Docker Desktop 에서는 외부 요청이 모두
 * 게이트웨이에서 온 것으로 보이므로, 게이트웨이를 믿는 순간 모든 외부 요청의 헤더를 믿게 된다.
 */
final class TrustedProxies {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$");

    private final List<IpAddressMatcher> matchers;
    private final String description;

    private TrustedProxies(List<IpAddressMatcher> matchers, String description) {
        this.matchers = matchers;
        this.description = description;
    }

    /** 설정값을 읽는다. 잘못된 값이면 기동을 막는다(조용히 무시하면 위조가 통하는 설정이 된다). */
    static TrustedProxies parse(String config) {
        if (config == null || config.isBlank()) {
            return new TrustedProxies(Collections.emptyList(), "(없음)");
        }
        List<IpAddressMatcher> result = new ArrayList<>();
        for (String raw : config.split(",")) {
            String cidr = raw.trim();
            if (cidr.isEmpty()) {
                continue;
            }
            String address = cidr.contains("/") ? cidr.substring(0, cidr.indexOf('/')) : cidr;
            if (!isIpLiteral(address)) {
                throw new IllegalStateException("TRUSTED_PROXIES 에 IP/CIDR 이 아닌 값이 있습니다: " + cidr);
            }
            result.add(new IpAddressMatcher(cidr));
        }
        return new TrustedProxies(List.copyOf(result), config.trim());
    }

    boolean isEmpty() {
        return matchers.isEmpty();
    }

    boolean contains(String address) {
        if (!isIpLiteral(address)) {
            return false;
        }
        for (IpAddressMatcher matcher : matchers) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * IP 표기인지 확인한다.
     * 헤더 값은 공격자가 정하므로, 호스트 이름이 매처(InetAddress)로 넘어가 DNS 조회가
     * 일어나지 않도록 IP 형태만 통과시킨다.
     */
    static boolean isIpLiteral(String value) {
        return value != null && (IPV4.matcher(value).matches() || IPV6.matcher(value).matches());
    }

    @Override
    public String toString() {
        return description;
    }
}
