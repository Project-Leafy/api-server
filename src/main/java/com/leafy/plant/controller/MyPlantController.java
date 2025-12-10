package com.leafy.plant.controller;

import com.leafy.plant.dto.MyPlantResponseDto;
import com.leafy.plant.dto.UpdatePlantStatusRequest;
import com.leafy.plant.dto.PlantDetailResponseDto;
import com.leafy.global.type.PlantStatus;
import com.leafy.plant.dto.UpdateMyPlantRequest;
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
import com.leafy.plant.dto.CreateMyPlantRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;

@Tag(name = "My Plant", description = "내 반려식물 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/my-plants")
public class MyPlantController {

    private final MyPlantService myPlantService;
    private final UserRepository userRepository; // User 정보를 가져오기 위해 필요

    private User getAuthenticatedUser(UserDetails userDetails) {
        String email = userDetails.getUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
    }

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
    @Operation(summary = "반려식물 등록", description = "식별된 식물 정보와 닉네임을 입력받아 내 반려식물로 저장합니다.")
    @PostMapping
    public ResponseEntity<MyPlantResponseDto> registerMyPlant(
            @RequestBody CreateMyPlantRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getAuthenticatedUser(userDetails);
        MyPlantResponseDto response = myPlantService.join(user, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "반려식물 삭제", description = "등록된 반려식물을 삭제합니다. (관련된 성장일지도 함께 삭제될 수 있습니다.)")
    @DeleteMapping("/{plantId}")
    public ResponseEntity<Void> deleteMyPlant(
            @PathVariable Long plantId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getAuthenticatedUser(userDetails);
        myPlantService.delete(plantId, user);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "반려식물 상세 조회", description = "특정 반려식물의 상세 정보(관리 팁, 적정 온도 등 포함)를 조회합니다.")
    @GetMapping("/{plantId}")
    public ResponseEntity<PlantDetailResponseDto> getMyPlantDetail(@PathVariable Long plantId) {
        // 81 라인의 변수 선언부도 변경해야 한다 이다.
        PlantDetailResponseDto response = myPlantService.getMyPlantDetail(plantId);
        return ResponseEntity.ok(response);
    }

    // ✅ 새로 추가: 반려식물 정보 업데이트
    @Operation(summary = "반려식물 정보 수정", description = "반려식물의 닉네임 또는 입양일을 수정합니다.")
    @PatchMapping("/{plantId}")
    public ResponseEntity<?> updateMyPlant(
            @PathVariable Long plantId,
            @RequestBody UpdateMyPlantRequest request, // 👈 Map 대신 DTO 사용
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            User user = getAuthenticatedUser(userDetails);
            // Service 메소드도 DTO를 받도록 수정 필요
            MyPlantResponseDto response = myPlantService.updateMyPlant(plantId, request, user);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("업데이트 실패: " + e.getMessage());
        }
    }

    // ✅ 새로 추가: 반려식물 상태 업데이트
    @Operation(summary = "반려식물 상태 업데이트", description = "반려식물의 상태를 HEALTHY 또는 WITHERED로 변경합니다.")
    @PatchMapping("/{myPlantId}/status")
    public ResponseEntity<MyPlantResponseDto> updateMyPlantStatus(
            @PathVariable Long myPlantId,
            @RequestBody UpdatePlantStatusRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getAuthenticatedUser(userDetails);
        MyPlantResponseDto response = myPlantService.updateMyPlantStatus(myPlantId, request.getStatus(), user);
        return ResponseEntity.ok(response);
    }

    // ✅ 새로 추가: 반려식물 상태 조회
    @Operation(summary = "반려식물 상태 조회", description = "특정 반려식물의 현재 상태를 조회합니다.")
    @GetMapping("/{myPlantId}/status")
    public ResponseEntity<PlantStatus> getMyPlantStatus(
            @PathVariable Long myPlantId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getAuthenticatedUser(userDetails);
        PlantStatus status = myPlantService.getMyPlantStatus(myPlantId, user);
        return ResponseEntity.ok(status);
    }
}
