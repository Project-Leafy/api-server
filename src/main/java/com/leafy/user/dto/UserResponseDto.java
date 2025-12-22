package com.leafy.user.dto;

import com.leafy.user.domain.Role;
import com.leafy.user.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponseDto {

    private Long userId;
    private String email;
    private String nickname;
    private String profilePhotoUrl;
    private Integer currentPlantsCount; // 현재 키우는 식물 수
    private Boolean onboardingStatus;   // 온보딩 완료 여부 (게스트/유저 구분용)
    private Role role;
    private LocalDateTime createdAt;    // 서비스 가입일 (회원 생성 시각)

    // User 엔티티를 받아서 DTO로 변환하는 정적 메서드 (편의성)
    public static UserResponseDto from(User user) {
        return UserResponseDto.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .currentPlantsCount(user.getCurrentPlantsCount())
                .onboardingStatus(user.getOnboardingStatus())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}