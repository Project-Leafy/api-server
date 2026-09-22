package com.leafy.auth.controller;

import com.leafy.auth.dto.AuthDtos.*;
import com.leafy.auth.service.AuthService;
import com.leafy.global.security.jwt.TokenInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "아이디·비밀번호 로그인, 회원가입, 계정 찾기, 탈퇴")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "아이디 중복 확인")
    @GetMapping("/check-login-id")
    public AvailabilityResponse checkLoginId(@RequestParam String loginId) {
        return new AvailabilityResponse(authService.isLoginIdAvailable(loginId));
    }

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @Operation(summary = "로그인", description = "성공하면 access_token 을 돌려준다. 이후 요청에 Authorization: Bearer 로 싣는다.")
    @PostMapping("/login")
    public TokenInfo login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "아이디 찾기", description = "가입 시 등록한 이메일과 닉네임이 일치하면 아이디를 돌려준다.")
    @PostMapping("/find-id")
    public FindIdResponse findId(@Valid @RequestBody FindIdRequest request) {
        return authService.findLoginId(request);
    }

    @Operation(summary = "비밀번호 재설정", description = "아이디와 이메일이 일치하면 새 비밀번호로 바꾼다.")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "비밀번호 변경 (로그인 필요)")
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "회원 탈퇴 (로그인 필요)", description = "비밀번호를 다시 확인한 뒤 계정과 모든 식물·일정·알림 데이터를 삭제한다.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal UserDetails userDetails,
                                         @Valid @RequestBody WithdrawRequest request) {
        authService.withdraw(userDetails.getUsername(), request);
        return ResponseEntity.noContent().build();
    }
}
