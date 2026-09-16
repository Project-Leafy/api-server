package com.leafy.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 인증 기능에서 요청을 거부할 때 쓰는 예외.
 *
 * <p>{@code clientMessage} 는 응답으로 나가는 문구이고, 로그에는 {@code reason} 이 남는다.
 * 로그인 실패처럼 원인을 사용자에게 자세히 알리면 안 되는 경우가 있어 둘을 분리했다.
 */
@Getter
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String reason;
    private final String clientMessage;

    private AuthException(HttpStatus status, String reason, String clientMessage) {
        super(reason + ": " + clientMessage);
        this.status = status;
        this.reason = reason;
        this.clientMessage = clientMessage;
    }

    public static AuthException duplicateLoginId() {
        return new AuthException(HttpStatus.CONFLICT, "duplicate_login_id", "이미 사용 중인 아이디입니다.");
    }

    public static AuthException duplicateEmail() {
        return new AuthException(HttpStatus.CONFLICT, "duplicate_email", "이미 가입된 이메일입니다.");
    }

    /** 아이디가 없는 경우와 비밀번호가 틀린 경우를 같은 문구로 응답해 아이디 존재 여부를 숨긴다. */
    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    public static AuthException wrongPassword() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "wrong_password", "비밀번호가 올바르지 않습니다.");
    }

    public static AuthException accountNotMatched() {
        return new AuthException(HttpStatus.NOT_FOUND, "account_not_matched", "입력한 정보와 일치하는 계정이 없습니다.");
    }

    public static AuthException notLocalAccount() {
        return new AuthException(HttpStatus.BAD_REQUEST, "not_local_account", "아이디·비밀번호로 가입한 계정이 아닙니다.");
    }
}
