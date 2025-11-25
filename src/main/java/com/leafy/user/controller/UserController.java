package com.leafy.user.controller;

import com.leafy.user.dto.UserResponseDto;
import com.leafy.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자 관련 API") // Swagger에 표시될 이름
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 프로필 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        // @AuthenticationPrincipal: 토큰에서 사용자 정보(이메일)를 꺼내옵니다.
        // SecurityConfig 설정 덕분에 로그인 안 한 사람은 여기까지 못 들어옵니다.

        String email = userDetails.getUsername(); // JWT 토큰의 subject(이메일) 추출
        UserResponseDto myInfo = userService.getMyInfo(email);

        return ResponseEntity.ok(myInfo);
    }
}