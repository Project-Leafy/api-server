package com.leafy.journal.controller;

import com.leafy.journal.dto.CreateGrowthRecordRequest;
import com.leafy.journal.dto.GrowthRecordResponse;
import com.leafy.journal.dto.UpdateGrowthRecordRequest;
import com.leafy.journal.service.GrowthRecordService;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GrowthRecordController {

    private final GrowthRecordService growthRecordService;
    
    // TODO: 임시 사용자 조회 로직. 실제 구현에서는 Spring Security의 Authentication 객체로 대체해야 합니다.
    private final UserRepository userRepository;

    // 임시로 첫 번째 사용자를 인증된 사용자로 간주하는 메서드
    private User getAuthenticatedUser() {
        // 데모 및 테스트를 위해 ID가 1L인 사용자를 조회합니다.
        // 이 부분은 실제 인증 로직으로 반드시 교체되어야 합니다.
        return userRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default user for testing not found. Please ensure user with ID 1 exists."));
    }

    /**
     * 성장 일지 생성 API
     * [POST] /api/v1/plants/{plantId}/journal
     */
    @PostMapping("/plants/{plantId}/journal")
    public ResponseEntity<GrowthRecordResponse> createGrowthRecord(
            @PathVariable Long plantId,
            @RequestBody CreateGrowthRecordRequest request) {
        User user = getAuthenticatedUser();
        GrowthRecordResponse response = growthRecordService.create(plantId, request, user);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * 특정 식물의 성장 일지 목록 조회 API
     * [GET] /api/v1/plants/{plantId}/journal
     */
    @GetMapping("/plants/{plantId}/journal")
    public ResponseEntity<List<GrowthRecordResponse>> getGrowthRecordsForPlant(@PathVariable Long plantId) {
        User user = getAuthenticatedUser();
        List<GrowthRecordResponse> responses = growthRecordService.findAllByPlantId(plantId, user);
        return ResponseEntity.ok(responses);
    }

    /**
     * 성장 일지 상세 조회 API
     * [GET] /api/v1/journal/{recordId}
     */
    @GetMapping("/journal/{recordId}")
    public ResponseEntity<GrowthRecordResponse> getGrowthRecord(@PathVariable Long recordId) {
        User user = getAuthenticatedUser();
        GrowthRecordResponse response = growthRecordService.findById(recordId, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 성장 일지 수정 API
     * [PATCH] /api/v1/journal/{recordId}
     */
    @PatchMapping("/journal/{recordId}")
    public ResponseEntity<GrowthRecordResponse> updateGrowthRecord(
            @PathVariable Long recordId,
            @RequestBody UpdateGrowthRecordRequest request) {
        User user = getAuthenticatedUser();
        GrowthRecordResponse response = growthRecordService.update(recordId, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 성장 일지 삭제 API
     * [DELETE] /api/v1/journal/{recordId}
     */
    @DeleteMapping("/journal/{recordId}")
    public ResponseEntity<Void> deleteGrowthRecord(@PathVariable Long recordId) {
        User user = getAuthenticatedUser();
        growthRecordService.delete(recordId, user);
        return ResponseEntity.noContent().build();
    }
}
