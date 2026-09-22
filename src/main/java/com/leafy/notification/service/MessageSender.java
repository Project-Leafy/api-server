package com.leafy.notification.service;

import com.leafy.user.domain.User;

/**
 * 사용자에게 알림 메시지를 전달하는 수단.
 *
 * <p>기본은 {@link InAppMessageSender}(앱 안 알림함)이고, kakao 프로필을 켜면
 * {@link KakaoMessageService}(카카오톡 나에게 보내기)로 바뀐다.
 *
 * <p>반환값이 true 면 호출한 쪽에서 알림 이력(Notification)을 저장한다.
 * 이 이력이 곧 앱 안 알림함의 내용이다.
 */
public interface MessageSender {

    boolean sendSelfMessage(User user, String messageContent);
}
