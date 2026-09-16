package com.leafy.global.security.oauth;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.leafy.user.domain.Role;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kakao.enabled", havingValue = "true")
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    // 관리자 이메일 상수 정의 : 일단 내꺼로 함
    private static final String ADMIN_EMAIL = "oksorry12@naver.com";
    /**
     * OAuth2 공급자로부터 사용자 정보를 로드하고, 데이터베이스에 사용자가 없으면 새로 생성합니다.
     *
     * @param userRequest OAuth2 사용자 요청
     * @return OAuth2User 객체
     * @throws OAuth2AuthenticationException OAuth2 인증 예외
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 디버깅 로그: 카카오 응답 전체 출력
        System.out.println("====== Kakao OAuth2 Attributes Start (for Debugging) ======");
        System.out.println(attributes);
        System.out.println("====== Kakao OAuth2 Attributes End ======");
        // --- 여기까지 디버깅 로그 ---

        String email = getEmail(registrationId, attributes);

        // 이메일 누락 시 인증 실패 처리
        if (email == null) {
            throw new OAuth2AuthenticationException("Email not provided by Kakao. Check required scopes.");
        }
        String nickname = getNickname(registrationId, attributes); //신규 회원 가입 시 닉네임을 데이터베이스에 저장

        // 이메일로 사용자를 찾거나, 없으면 새로 생성
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    // (Role로 신규인지 아닌지 판단) 신규 가입 시 Role 분기
                    Role initialRole = ADMIN_EMAIL.equals(email) ? Role.ADMIN : Role.GUEST;

                    return userRepository.save(User.builder()
                            .email(email)
                            .nickname(nickname)
                            .oauthProvider(registrationId)
                            .oauthProviderId(String.valueOf(attributes.get("id")))
                            .role(initialRole) // 확정된 Role 할당
                            .build());
                });

        return new CustomOAuth2User(user, attributes);
    }

    /**
     * OAuth2 공급자로부터 이메일을 추출합니다.
     *
     * @param registrationId OAuth2 공급자 ID
     * @param attributes     사용자 속성
     * @return 이메일
     */
    private String getEmail(String registrationId, Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            // ✨ 3. NullPointerException 방지
            if (kakaoAccount != null) {
                return (String) kakaoAccount.get("email");
            }
        }
        return null;
    }

    /**
     * OAuth2 공급자로부터 닉네임을 추출합니다.
     *
     * @param registrationId OAuth2 공급자 ID
     * @param attributes     사용자 속성
     * @return 닉네임
     */
    private String getNickname(String registrationId, Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
            // ✨ NullPointerException 방지
            if (properties != null) {
                return (String) properties.get("nickname");
            }
        }
        return null;
    }
}

