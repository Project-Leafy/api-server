package com.leafy.recommendation.controller;

import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.recommendation.dto.RecommendationRequest;
import com.leafy.recommendation.dto.RecommendationResponseDto;
import com.leafy.recommendation.service.RecommendationService;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Recommendation", description = "맞춤 식물 추천 API")
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final UserRepository userRepository;

    // JWT 토큰에서 사용자 정보 조회 헬퍼 메서드
    private User getAuthenticatedUser(UserDetails userDetails) {
        String email = userDetails.getUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
    }

    @Operation(summary = "맞춤 식물 추천받기", description = "사용자의 환경/성향 설문 결과를 바탕으로(배치 분석 결과 반영) 적합한 식물을 추천합니다.")
    @PostMapping
    public ResponseEntity<List<RecommendationResponseDto>> recommendPlants(
            @RequestBody RecommendationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. 로그인 유저 확인
        User user = getAuthenticatedUser(userDetails);

        // 2. 서비스 호출 (Hybrid 추천 로직)
        List<RecommendationResponseDto> recommendations = recommendationService.recommendPlants(user, request);

        // 3. 결과 반환
        return ResponseEntity.ok(recommendations);
    }
}