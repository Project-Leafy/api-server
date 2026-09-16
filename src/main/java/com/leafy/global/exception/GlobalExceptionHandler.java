package com.leafy.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 요청이 왜 거부됐는지를 로그에 남기는 예외 핸들러.
 *
 * <p>이 클래스가 없으면 검증·바인딩 실패가 Spring 기본 동작으로 400을 내보내면서
 * <b>앱 로그에는 아무 기록도 남지 않는다.</b> 그 결과 스캔성 요청이 전부 차단된 상황에서도
 * "차단됐다"는 근거가 로그에 없어, 분석 주체가 과잉 대응으로 기울게 된다.
 *
 * <p>따라서 차단된 요청은 WARN 으로 남기되 스택트레이스는 남기지 않는다
 * (정상적인 방어 동작이므로 노이즈가 된다). 반대로 처리되지 않은 예외는
 * 실제 결함일 수 있으므로 ERROR 와 스택트레이스를 함께 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 검증 실패 — 잘못된 입력이 막힌 경우 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining(", "));

        return blocked(request, HttpStatus.BAD_REQUEST, "validation_failed", detail);
    }

    /** 타입 불일치 — 예: 숫자 자리에 문자열이나 SQL 구문이 들어온 경우 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        String detail = "parameter '" + e.getName() + "' 타입 불일치";
        return blocked(request, HttpStatus.BAD_REQUEST, "type_mismatch", detail);
    }

    /** 필수 파라미터 누락 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException e, HttpServletRequest request) {

        String detail = "필수 parameter '" + e.getParameterName() + "' 누락";
        return blocked(request, HttpStatus.BAD_REQUEST, "missing_parameter", detail);
    }

    /** 조회 대상 없음 */
    @ExceptionHandler({EntityNotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(
            RuntimeException e, HttpServletRequest request) {

        return blocked(request, HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    /** 존재하지 않는 경로 — 스캐너가 경로를 훑을 때 대량으로 발생한다. */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNoHandler(
            Exception e, HttpServletRequest request) {

        return blocked(request, HttpStatus.NOT_FOUND, "no_handler", "존재하지 않는 경로입니다.");
    }

    /**
     * 그 외 처리되지 않은 예외.
     *
     * <p>주의: 여기서 모든 예외를 500으로 바꿔버리면 4xx로 끝났어야 할 요청이
     * 서버 오류로 둔갑한다. 그러면 "차단된 요청"과 "서버가 터진 요청"을 구분할 수 없어
     * 이 작업의 목적 자체가 무너진다. 따라서 상태코드를 가진 예외는 그 값을 보존한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception e, HttpServletRequest request) {

        // Spring이 상태코드를 정해둔 예외(ResponseStatusException 등)는 그 코드를 따른다.
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            if (status.is4xxClientError()) {
                return blocked(request, status, "client_error", e.getMessage());
            }
        }

        log.error("[UNHANDLED] {} {} 처리 중 예외 발생",
                request.getMethod(), request.getRequestURI(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                        "서버 내부 오류가 발생했습니다."));
    }

    /**
     * 차단된 요청을 공통 형식으로 기록한다.
     * "BLOCKED" 라는 고정 토큰을 붙여, 텍스트 로그 상태에서도 탐지 룰이 잡을 수 있게 한다.
     */
    private ResponseEntity<Map<String, Object>> blocked(
            HttpServletRequest request, HttpStatus status, String reason, String detail) {

        log.warn("[BLOCKED] {} {} 거부됨 (status={}, reason={}, detail={})",
                request.getMethod(), request.getRequestURI(), status.value(), reason, detail);

        return ResponseEntity.status(status).body(body(status, reason, detail));
    }

    /** 클라이언트 응답 본문. 내부 구조가 드러나지 않도록 최소한만 담는다. */
    private Map<String, Object> body(HttpStatus status, String reason, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("reason", reason);
        body.put("message", detail == null ? "" : detail);
        return body;
    }

    private String describeFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
