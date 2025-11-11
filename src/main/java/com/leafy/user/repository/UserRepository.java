package com.leafy.user.repository;

import com.leafy.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일로 사용자를 조회합니다. (소셜 로그인 시 사용)
    Optional<User> findByEmail(String email);

    // 2. 닉네임 중복 확인 시 사용
    boolean existsByNickname(String nickname);
}