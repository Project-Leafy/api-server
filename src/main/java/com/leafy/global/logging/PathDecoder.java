package com.leafy.global.logging;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * 액세스 로그에 남길 요청 경로를 디코딩한다.
 *
 * <p>경로 변수에 실린 공격({@code /1%27%20UNION%20SELECT...})도 탐지 룰이 평문으로 읽을 수 있게 한다.
 * 쿼리와 달리 경로에서 {@code +} 는 공백이 아니므로 URLDecoder 가 아니라 {@code %XX} 만 푸는
 * UriUtils 를 쓴다. 디코딩은 한 번만 한다({@code %2527} 은 {@code %27} 로 남는다).
 */
final class PathDecoder {

    private PathDecoder() {
    }

    static String decode(String rawPath) {
        if (rawPath == null) {
            return null;
        }
        try {
            return UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 잘못된 인코딩(%ZZ 등)은 공격 시도일 수 있으므로 버리지 않고 원본을 남긴다.
            return rawPath;
        }
    }
}
