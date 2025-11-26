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

    @Id // 2. PK(기본 키)임을 명시 -> user_id 가 PK임! Id가 아니라! 이거 주의
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 3. 'bigserial' (auto-increment) 전략 사용
    @Column(name = "user_id")
    private Long userId;

    @Column(unique = true, nullable = false, length = 255)
    private String email; // 사용자의 이메일 주소이며, 로그인 시 사용됩니다.

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(length = 255)
    private String oauthProvider; // 소셜 로그인 제공자 (e.g., kakao)

    @Column(length = 255)
    private String oauthProviderId; // 소셜 로그인 제공자의 사용자 ID

    @Column(name = "profile_photo_url", length = 2048)
    private String profilePhotoUrl;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    private LocalDateTime refreshTokenExpiresAt;

    //    카카오 API 연동을 위한 토큰 필드
    @Column(name = "kakao_access_token", columnDefinition = "TEXT")
    private String kakaoAccessToken;

    @Column(name = "kakao_refresh_token", columnDefinition = "TEXT")
    private String kakaoRefreshToken;

    @Column(nullable = false)
    private Integer currentPlantsCount = 0; // 4. default: 0 설정 (Wrapper 타입보단 primitive 타입 권장)

    @Column(nullable = false)
    private Boolean onboardingStatus = false; // default: false

    private LocalDateTime lastLoginAt;

    // Role 필드 추가
    @Enumerated(EnumType.STRING) // Enum 이름을 DB에 문자열로 저장
    @Column(nullable = false, length = 20)
    private Role role;

    // (빌더 패턴 등 필요한 메서드 추가)
    @Builder
    public User(String email, String nickname, String oauthProvider, String oauthProviderId, String profilePhotoUrl, Role role) {
        this.email = email;
        this.nickname = nickname;
        this.oauthProvider = oauthProvider;
        this.oauthProviderId = oauthProviderId;
        this.profilePhotoUrl = profilePhotoUrl;
        this.role = role; // ✨ Role 추가
    }

    // 카카오 토큰 저장/갱신 메서드 ---
    public void updateKakaoToken(String accessToken, String refreshToken) {
        this.kakaoAccessToken = accessToken;
        // Refresh Token은 매번 새로 발급되지 않을 수 있으므로 null이 아닐 때만 업데이트
        if (refreshToken != null) {
            this.kakaoRefreshToken = refreshToken;
        }
    }
}