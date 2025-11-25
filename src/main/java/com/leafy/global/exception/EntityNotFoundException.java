package com.leafy.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * DB에서 원하는 데이터를 찾지 못했을 때 발생하는 예외
 * 결과: 404 Not Found 에러 반환
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}