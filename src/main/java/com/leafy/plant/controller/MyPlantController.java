package com.leafy.plant.controller;

import com.leafy.plant.dto.MyPlantResponseDto;
import com.leafy.plant.service.MyPlantService;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/my-plants")
public class MyPlantController {

    private final MyPlantService myPlantService;
    private final UserRepository userRepository; // User 정보를 가져오기 위해 필요

    @GetMapping
    @PreAuthorize("isAuthenticated()") // 인증된 사용자만 호출 가능
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
