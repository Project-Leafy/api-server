package com.leafy.notification.service;

import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.notification.dto.NotificationResponse;
import com.leafy.notification.repository.NotificationRepository;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 앱 안 알림함 조회·읽음 처리. */
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String email) {
        return notificationRepository.findAllByUserOrderByCreatedAtDesc(findUser(email)).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String email) {
        return notificationRepository.countByUserAndIsReadFalse(findUser(email));
    }

    @Transactional
    public void markAsRead(String email, Long notificationId) {
        // 다른 사용자의 알림 ID를 넣어도 찾지 못하도록 본인 조건을 함께 건다.
        notificationRepository.findByNotificationIdAndUser(notificationId, findUser(email))
                .orElseThrow(() -> new EntityNotFoundException("알림을 찾을 수 없습니다. id=" + notificationId))
                .markAsRead();
    }

    @Transactional
    public void markAllAsRead(String email) {
        notificationRepository.findAllByUserAndIsReadFalse(findUser(email))
                .forEach(n -> n.markAsRead());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
