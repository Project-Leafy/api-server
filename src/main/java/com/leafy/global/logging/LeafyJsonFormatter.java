package com.leafy.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 로그를 한 줄 JSON으로 출력하는 포맷터.
 *
 * <p>수집 계층(fluentd → Loki)이 상태코드 같은 값을 <b>필드</b>로 읽을 수 있어야
 * "시간창 내 4xx 100%, 5xx 0" 같은 근거를 뽑을 수 있다. 비구조화 텍스트로는
 * 정규식 매칭만 가능해 이런 집계가 불가능하다.
 *
 * <p>{@code logging.structured.format.console} 설정으로 이 클래스를 지정할 때만
 * 활성화된다. 지정하지 않으면 기존 텍스트 포맷 그대로 출력된다.
 * 수집 계층이 아직 텍스트를 파싱하고 있으므로, 양쪽 전환 시점을 맞추기 위해
 * 환경변수로 켜고 끌 수 있게 했다.
 */
public class LeafyJsonFormatter implements StructuredLogFormatter<ILoggingEvent> {

    /** ISO8601 + offset. 수집 계층과 타임존 해석이 어긋나지 않도록 offset을 반드시 포함한다. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String format(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();

        fields.put("timestamp", ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault()).format(TIMESTAMP));
        fields.put("level", event.getLevel().toString());
        fields.put("logger", event.getLoggerName());
        fields.put("thread", event.getThreadName());
        fields.put("message", event.getFormattedMessage());

        // MDC에 담긴 요청 맥락(status, source_ip, uri 등)을 최상위 필드로 올린다.
        // 탐지 룰이 `| json | status=400` 형태로 바로 질의할 수 있게 하기 위함이다.
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null) {
            for (Map.Entry<String, String> entry : mdc.entrySet()) {
                fields.putIfAbsent(entry.getKey(), coerce(entry.getKey(), entry.getValue()));
            }
        }

        // 스택트레이스는 개행이 포함된 문자열이지만, JSON 문자열로 인코딩되면서
        // 개행이 \n 으로 이스케이프되므로 로그는 여전히 한 줄로 유지된다.
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            fields.put("exception_class", throwable.getClassName());
            fields.put("exception_message", throwable.getMessage());
            fields.put("stack_trace", ThrowableProxyUtil.asString(throwable));
        }

        try {
            return MAPPER.writeValueAsString(fields) + "\n";
        } catch (Exception e) {
            // 포맷팅 실패로 로그를 통째로 잃지 않도록 최소한의 형태로 떨어뜨린다.
            return "{\"level\":\"ERROR\",\"message\":\"log format failed\"}\n";
        }
    }

    /**
     * 숫자로 다뤄야 하는 필드는 숫자 타입으로 내보낸다.
     * 문자열로 나가면 Loki에서 {@code status >= 400} 같은 비교 질의가 안 된다.
     */
    private Object coerce(String key, String value) {
        if (value == null) {
            return null;
        }
        if ("status".equals(key) || "duration_ms".equals(key)) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }
}
