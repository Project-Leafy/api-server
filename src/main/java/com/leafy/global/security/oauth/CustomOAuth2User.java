package com.leafy.global.security.oauth;

import com.leafy.user.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
// ✨ 1. import 추가
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
// ✨ 2. import 추가
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

    // ✨ 3. [수정됨] null 대신 실제 권한(Role)을 반환
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // user.getRole() (예: Role.ADMIN)을 "ROLE_ADMIN" 문자열로 변환
        return Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    @Override
    public String getName() {
        // (참고) Spring Security에서 'name'은 고유 식별자를 의미하기도 한다.
        // user.getNickname() 보다는 user.getEmail()이나 user.getUserId().toString()이 더 적합할 수 있으나,
        // 현재 닉네임으로 되어있어도 치명적인 오류는 아니다.
        return user.getNickname();
    }
}