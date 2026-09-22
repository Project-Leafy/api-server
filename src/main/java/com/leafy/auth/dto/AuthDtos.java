package com.leafy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 로컬 인증 API의 요청·응답 DTO 모음.
 *
 * <p>JSON 필드명은 전역 설정(SNAKE_CASE)을 따른다. 예: loginId -> login_id
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 아이디: 영문 소문자로 시작하는 4~20자 (소문자·숫자·밑줄) */
    private static final String LOGIN_ID_REGEX = "^[a-z][a-z0-9_]{3,19}$";

    /** 비밀번호: 8~64자, 영문과 숫자를 각각 1자 이상 포함 */
    private static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";

    private static final String LOGIN_ID_MESSAGE = "아이디는 영문 소문자로 시작하는 4~20자(소문자·숫자·_)여야 합니다.";
    private static final String PASSWORD_MESSAGE = "비밀번호는 영문과 숫자를 포함한 8~64자여야 합니다.";

    public record SignupRequest(
            @NotBlank @Pattern(regexp = LOGIN_ID_REGEX, message = LOGIN_ID_MESSAGE) String loginId,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @NotBlank @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 255) String email,
            @NotBlank @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.") String nickname) {
    }

    public record LoginRequest(
            @NotBlank String loginId,
            @NotBlank String password) {
    }

    public record FindIdRequest(
            @NotBlank @Email String email,
            @NotBlank String nickname) {
    }

    public record FindIdResponse(String loginId) {
    }

    public record ResetPasswordRequest(
            @NotBlank String loginId,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record WithdrawRequest(
            @NotBlank String password) {
    }

    public record AvailabilityResponse(boolean available) {
    }

    public record SignupResponse(Long userId, String loginId, String nickname) {
    }
}
