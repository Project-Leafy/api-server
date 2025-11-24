package com.leafy.plant.controller;

import com.leafy.plant.dto.MyPlantResponseDto;
import com.leafy.plant.service.MyPlantService;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "My Plant", description = "내 반려식물 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/my-plants")
public class MyPlantController {

    private final MyPlantService myPlantService;
    private final UserRepository userRepository; // User 정보를 가져오기 위해 필요

    @Operation(summary = "내 식물 목록 조회", description = "내가 등록한 모든 반려식물의 리스트를 최신순으로 조회")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MyPlantResponseDto>> getMyPlantList(Authentication authentication) {
        // Authentication 객체에서 사용자 이메일(principal name)을 가져옵니다.
        String userEmail = authentication.getName();

        // 이메일을 통해 User 엔티티를 조회합니다.
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + userEmail));

        List<MyPlantResponseDto> myPlants = myPlantService.findMyPlants(user);
        return ResponseEntity.ok(myPlants);
    }
}
