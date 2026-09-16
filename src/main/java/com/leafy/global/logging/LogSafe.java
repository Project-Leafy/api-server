package com.leafy.global.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그에 남기기 전에 값을 안전하게 다듬는다.
 *
 * <p>두 가지를 처리한다.
 * <ol>
 *   <li><b>로그 인젝션 방어</b> — 공격자가 {@code %0a} 를 보내면 디코딩 후 개행이 되어
 *       로그 한 줄이 두 줄로 쪼개진다. 이를 이용해 가짜 로그 줄을 위조할 수 있으므로
 *       제어문자를 이스케이프해 항상 한 줄을 보장한다.</li>
 *   <li><b>비밀값 마스킹</b> — 토큰·API 키·비밀번호가 쿼리에 실려 오면 그대로 로그에 남는다.
 *       로그는 수집되어 외부(Loki)에 장기 보관되므로 값을 가린다.</li>
 * </ol>
 */
public final class LogSafe {

    /** 값을 가려야 하는 파라미터 이름. 대소문자를 구분하지 않는다. */
    private static final Pattern SECRET_PARAM = Pattern.compile(
            "(?i)\\b(access_?token|refresh_?token|id_?token|token|secret|password|passwd|pwd"
                    + "|api_?key|client_?secret|authorization|credential|session)"
                    + "\\s*=\\s*([^&\\s]*)");

    private static final String MASK = "***";

    private LogSafe() {
    }

    /**
     * 제어문자를 이스케이프해 한 줄을 보장한다. null 은 "-" 로 남긴다.
     */
    public static String sanitize(String value) {
        if (value == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // 그 외 제어문자는 눈에 보이는 형태로 치환한다.
                    if (c < 0x20 || c == 0x7f) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 쿼리 문자열용. 비밀값을 가린 뒤 제어문자를 이스케이프한다.
     */
    static String sanitizeQuery(String value) {
        if (value == null) {
            return "-";
        }
        return sanitize(maskSecrets(value));
    }

    private static String maskSecrets(String value) {
        Matcher matcher = SECRET_PARAM.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + "=" + MASK));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
