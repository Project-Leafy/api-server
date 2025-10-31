package com.leafy.user.repository;

import com.leafy.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 1. 카카오 ID로 사용자 조회 (로그인 시 사용)
    Optional<User> findByKakaoId(String kakaoId);

    // 2. 닉네임 중복 확인 시 사용
    boolean existsByNickname(String nickname);
}