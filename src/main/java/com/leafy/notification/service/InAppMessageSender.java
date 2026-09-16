package com.leafy.notification.service;

import com.leafy.user.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 앱 안 알림함 방식의 발송.
 *
 * <p>외부로 보내지 않는다. 호출한 쪽이 성공(true)을 받아 Notification 을 저장하면
 * 사용자는 알림함 API로 그 내용을 조회한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.kakao.enabled", havingValue = "false", matchIfMissing = true)
public class InAppMessageSender implements MessageSender {

    @Override
    public boolean sendSelfMessage(User user, String messageContent) {
        log.info("[NOTIFY] 알림함 적재 userId={} length={}", user.getUserId(),
                messageContent == null ? 0 : messageContent.length());
        return true;
    }
}
