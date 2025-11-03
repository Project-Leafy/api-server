// 경로: api-server/src/main/java/com/leafy/global/security/oauth/CustomOAuth2User.java
package com.leafy.global.security.oauth;

import com.leafy.user.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
public class CustomOAuth2User implements OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // user.getRole() (예: Role.ADMIN)을 "ROLE_ADMIN" 문자열로 변환
        return Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    @Override
    public String getName() {
        // ⬅️ 수정: Principal의 값을 닉네임(user.getNickname()) 대신 이메일로 설정
        // 이메일은 DB에서 사용자를 조회하는 PK/Unique Key의 역할을 하므로, JWT의 Subject로 적합합니다.
        return user.getEmail(); // ⬅️ 수정 완료
    }

    // JWTTokenProvider에서 사용하기 위해 email Getter를 명시적으로 추가 (선택적)
    public String getEmail() {
        return user.getEmail();
    }
}