package com.leafy.user.service;

import com.leafy.global.exception.ResourceNotFoundException;
import com.leafy.user.domain.User;
import com.leafy.user.dto.UserResponseDto;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 조회 전용 트랜잭션 (성능 최적화)
public class UserService {

    private final UserRepository userRepository;

    // 내 정보 조회 기능
    public UserResponseDto getMyInfo(String email) {
        // 1. 이메일로 유저 찾기 (없으면 예외 발생)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

        // 2. DTO로 변환해서 반환
        return UserResponseDto.from(user);
    }
}