package com.leafy.global.logging;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 액세스 로그에 남길 쿼리 문자열을 디코딩한다.
 *
 * <p>공격 도구는 {@code ' OR 1=1} 을 {@code %27%20OR%201%3D1} 로 인코딩해 보낸다.
 * 인코딩된 채로 로그에 남으면 탐지 룰이 {@code union select} 같은 패턴을 읽지 못한다.
 *
 * <p>디코딩본은 탐지 룰이 보고, 원본은 증거 보존용으로 따로 남긴다.
 * 디코딩 결과에 섞여 들어오는 개행·제어문자는 {@link LogSafe} 가 이스케이프해
 * 로그 한 줄이 쪼개지거나 위조되는 것을 막는다.
 */
final class QueryDecoder {

    private QueryDecoder() {
    }

    static String decode(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        try {
            return URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 잘못된 인코딩(%ZZ 등)은 디코딩이 불가능하다.
            // 공격 시도일 수 있으므로 버리지 않고 원본을 그대로 돌려준다.
            return rawQuery;
        }
    }
}
