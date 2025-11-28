package com.leafy.user.controller;

import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.user.domain.User;
import com.leafy.user.dto.UserResponseDto;
import com.leafy.user.repository.UserRepository;
import com.leafy.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "사용자 관련 API") // Swagger에 표시될 이름
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 프로필 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        // @AuthenticationPrincipal: 토큰에서 사용자 정보(이메일)를 꺼내옵니다.
        // SecurityConfig 설정 덕분에 로그인 안 한 사람은 여기까지 못 들어옵니다.

        String email = userDetails.getUsername(); // JWT 토큰의 subject(이메일) 추출
        UserResponseDto myInfo = userService.getMyInfo(email);

        return ResponseEntity.ok(myInfo);
    }

    @Operation(summary = "사용자 위치 업데이트", description = "스마트 날씨 알림을 위해 사용자의 현재 위치(위도, 경도)를 저장합니다.")
    @PutMapping("/location")
    public ResponseEntity<String> updateLocation(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // User 엔티티에 추가해둔 메서드 재사용
        user.updateLocation(lat, lon);
        userRepository.save(user); // @Transactional 없으면 명시적 저장 필요

        return ResponseEntity.ok("위치 정보가 업데이트되었습니다.");
    }
}