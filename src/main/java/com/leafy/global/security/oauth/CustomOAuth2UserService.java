package com.leafy.global.security.oauth;

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
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

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

        String email = getEmail(registrationId, attributes);

        // 이메일을 기반으로 사용자를 찾거나 새로 생성합니다.
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .nickname(getNickname(registrationId, attributes))
                        .oauthProvider(registrationId)
                        .oauthProviderId(String.valueOf(attributes.get("id")))
                        .build()));

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
            return (String) kakaoAccount.get("email");
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
            return (String) properties.get("nickname");
        }
        return null;
    }
}
