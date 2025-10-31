// com/leafy/user/domain/User.java

package com.leafy.user.domain;

import com.leafy.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users") // 1. ERD의 'users' 테이블과 매핑
public class User extends BaseTimeEntity {

    @Id // 2. PK(기본 키)임을 명시
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 3. 'bigserial' (auto-increment) 전략 사용
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "kakao_id", unique = true, nullable = false, length = 255)
    private String kakaoId;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(name = "profile_photo_url", length = 2048)
    private String profilePhotoUrl;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    private LocalDateTime refreshTokenExpiresAt;

    @Column(nullable = false)
    private Integer currentPlantsCount = 0; // 4. default: 0 설정 (Wrapper 타입보단 primitive 타입 권장)

    @Column(nullable = false)
    private Boolean onboardingStatus = false; // default: false

    private LocalDateTime lastLoginAt;

    // (빌더 패턴 등 필요한 메서드 추가)
    @Builder
    public User(String kakaoId, String nickname, String profilePhotoUrl) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.profilePhotoUrl = profilePhotoUrl;
    }
}