package com.leafy.auth.service;

import com.leafy.auth.dto.AuthDtos.*;
import com.leafy.auth.exception.AuthException;
import com.leafy.global.logging.LogSafe;
import com.leafy.global.security.jwt.JwtTokenProvider;
import com.leafy.global.security.jwt.TokenInfo;
import com.leafy.notification.repository.NotificationRepository;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.recommendation.repository.RecommendationRepository;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 아이디·비밀번호 기반 로컬 인증.
 *
 * <p>로그에는 비밀번호를 절대 남기지 않는다. 대신 로그인·재설정 실패는 아이디와 사유를
 * WARN 으로 남겨, 반복 실패(무차별 대입 시도)를 로그만으로 관측할 수 있게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final MyPlantRepository myPlantRepository;
    private final NotificationRepository notificationRepository;
    private final RecommendationRepository recommendationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public boolean isLoginIdAvailable(String loginId) {
        return !userRepository.existsByLoginId(loginId);
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw AuthException.duplicateLoginId();
        }
        if (userRepository.existsByEmail(request.email())) {
            throw AuthException.duplicateEmail();
        }

        User user = userRepository.save(User.createLocal(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.email(),
                request.nickname()));

        log.info("[AUTH] 회원가입 완료 userId={} loginId={}", user.getUserId(), user.getLoginId());
        return new SignupResponse(user.getUserId(), user.getLoginId(), user.getNickname());
    }

    @Transactional
    public TokenInfo login(LoginRequest request) {
        String loginId = LogSafe.sanitize(request.loginId());
        User user = userRepository.findByLoginId(request.loginId()).orElse(null);

        if (user == null) {
            log.warn("[AUTH] 로그인 실패 loginId={} reason=unknown_login_id", loginId);
            throw AuthException.invalidCredentials();
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("[AUTH] 로그인 실패 loginId={} userId={} reason=wrong_password", loginId, user.getUserId());
            throw AuthException.invalidCredentials();
        }

        user.recordLogin();
        log.info("[AUTH] 로그인 성공 loginId={} userId={}", loginId, user.getUserId());
        return issueToken(user);
    }

    public FindIdResponse findLoginId(FindIdRequest request) {
        User user = userRepository.findByEmailAndNickname(request.email(), request.nickname())
                .filter(u -> u.getLoginId() != null)
                .orElseThrow(() -> {
                    log.warn("[AUTH] 아이디 찾기 실패 reason=account_not_matched");
                    return AuthException.accountNotMatched();
                });

        log.info("[AUTH] 아이디 찾기 성공 userId={}", user.getUserId());
        return new FindIdResponse(maskLoginId(user.getLoginId()));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String loginId = LogSafe.sanitize(request.loginId());
        User user = userRepository.findByLoginId(request.loginId())
                .filter(u -> u.getEmail().equalsIgnoreCase(request.email()))
                .orElseThrow(() -> {
                    log.warn("[AUTH] 비밀번호 재설정 실패 loginId={} reason=account_not_matched", loginId);
                    return AuthException.accountNotMatched();
                });

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        log.info("[AUTH] 비밀번호 재설정 완료 loginId={} userId={}", loginId, user.getUserId());
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findLocalUserByEmail(email);
        verifyPassword(user, request.currentPassword(), "비밀번호 변경");

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        log.info("[AUTH] 비밀번호 변경 완료 userId={}", user.getUserId());
    }

    /**
     * 회원 탈퇴. 사용자에게 딸린 데이터를 참조 순서에 맞춰 지운다.
     *
     * <p>알림은 식물·일정·일지·진단을 참조하므로 가장 먼저 지운다.
     * 내 식물을 지우면 일정·성장 기록·진단 이력은 엔티티의 cascade 설정으로 함께 지워진다.
     */
    @Transactional
    public void withdraw(String email, WithdrawRequest request) {
        User user = findLocalUserByEmail(email);
        verifyPassword(user, request.password(), "회원 탈퇴");

        Long userId = user.getUserId();
        notificationRepository.deleteAllByUser(user);
        recommendationRepository.deleteAllByUser(user);
        myPlantRepository.deleteAll(myPlantRepository.findAllByUser(user));
        userRepository.delete(user);

        log.info("[AUTH] 회원 탈퇴 완료 userId={}", userId);
    }

    private User findLocalUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(AuthException::accountNotMatched);
        if (user.getPasswordHash() == null) {
            throw AuthException.notLocalAccount();
        }
        return user;
    }

    private void verifyPassword(User user, String rawPassword, String action) {
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("[AUTH] {} 실패 userId={} reason=wrong_password", action, user.getUserId());
            throw AuthException.wrongPassword();
        }
    }

    /**
     * 기존 JWT 구조를 그대로 쓴다. 토큰 subject 는 이메일이고, 다른 API들이
     * {@code userDetails.getUsername()} 을 이메일로 해석하고 있으므로 맞춰야 한다.
     */
    private TokenInfo issueToken(User user) {
        var authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        return jwtTokenProvider.generateToken(authentication);
    }

    /** 아이디 일부를 가린다. 예: leafyuser -> le*****er */
    static String maskLoginId(String loginId) {
        if (loginId.length() <= 4) {
            return loginId.substring(0, 2) + "*".repeat(loginId.length() - 2);
        }
        int visible = 2;
        return loginId.substring(0, visible)
                + "*".repeat(loginId.length() - visible * 2)
                + loginId.substring(loginId.length() - visible);
    }
}
