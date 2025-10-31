package com.leafy.recommendation.repository;

import com.leafy.recommendation.domain.Recommendation;
import com.leafy.user.domain.User; // User 엔티티 import
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    // 1. 특정 사용자의 추천 프로필 조회 (User 객체 기준)
    Optional<Recommendation> findByUser(User user);

    // 2. (대안) 사용자 ID 기준 조회
    Optional<Recommendation> findByUserId(Long userId);
}