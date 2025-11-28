package com.leafy.notification.repository;

import com.leafy.notification.domain.Notification;
import com.leafy.user.domain.User; // User 엔티티 import
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 1. 특정 사용자의 모든 알림 목록 조회 (알림함)
    // 엔티티에 정의한 인덱스(user_id, created_at)를 활용하기 위해
    // OrderByCreatedAtDesc (최신순) 정렬
    List<Notification> findAllByUserOrderByCreatedAtDesc(User user);
    
    // 2. (대안) 사용자 ID 기준 조회
    // List<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    // 3. 특정 사용자의 읽지 않은(isRead = false) 알림 개수 조회
    long countByUserAndIsReadFalse(User user);
}