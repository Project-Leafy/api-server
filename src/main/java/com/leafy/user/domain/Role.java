package com.leafy.user.domain;

/**
 * 사용자 권한을 정의하는 Enum이다.
 * Spring Security 인가(Authorization) 처리에 사용된다.
 */
public enum Role {
    /** 임시 사용자: 최초 소셜 로그인 후 온보딩이 완료되지 않은 상태이다. */
    GUEST,
    /** 일반 사용자: 온보딩을 완료하고 서비스를 정상적으로 이용하는 사용자이다. */
    USER,
    /** 관리자: 시스템 관리 권한을 가진 사용자이다. */
    ADMIN;
}
