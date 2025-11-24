package com.leafy.journal.controller;

import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.journal.dto.CreateGrowthRecordRequest;
import com.leafy.journal.dto.GrowthRecordResponse;
import com.leafy.journal.dto.UpdateGrowthRecordRequest;
import com.leafy.journal.service.GrowthRecordService;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Growth Record", description = "성장일지 관리 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GrowthRecordController {

    private final GrowthRecordService growthRecordService;
    private final UserRepository userRepository;

    //jwt 토큰에서 사용자 정보 조회
    private User getAuthenticatedUser(UserDetails userDetails) {
        String email = userDetails.getUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
    }


    @Operation(summary = "성장일지 생성", description = "특정 식물에 대한 새로운 성장 기록을 작성")
    @PostMapping("/plants/{plantId}/journal")
    public ResponseEntity<GrowthRecordResponse> createGrowthRecord(
            @PathVariable Long plantId,
            @RequestBody CreateGrowthRecordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getAuthenticatedUser(userDetails);
        GrowthRecordResponse response = growthRecordService.create(plantId, request, user);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }


    @Operation(summary = "식물별 일지 목록 조회", description = "특정 식물의 모든 성장일지를 최신순으로 조회합니다.")
    @GetMapping("/plants/{plantId}/journal")
    public ResponseEntity<List<GrowthRecordResponse>> getGrowthRecordsForPlant(
            @PathVariable Long plantId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        List<GrowthRecordResponse> responses = growthRecordService.findAllByPlantId(plantId, user);
        return ResponseEntity.ok(responses);
    }


    @Operation(summary = "일지 상세 조회", description = "특정 성장일지 하나의 상세 내용을 조회합니다.")
    @GetMapping("/journal/{recordId}")
    public ResponseEntity<GrowthRecordResponse> getGrowthRecord(
            @PathVariable Long recordId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        GrowthRecordResponse response = growthRecordService.findById(recordId, user);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "일지 수정", description = "기존 성장일지의 내용을 수정합니다.")
    @PatchMapping("/journal/{recordId}")
    public ResponseEntity<GrowthRecordResponse> updateGrowthRecord(
            @PathVariable Long recordId,
            @RequestBody UpdateGrowthRecordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        GrowthRecordResponse response = growthRecordService.update(recordId, request, user);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "일지 삭제", description = "특정 성장일지를 삭제합니다.")
    @DeleteMapping("/journal/{recordId}")
    public ResponseEntity<Void> deleteGrowthRecord(
            @PathVariable Long recordId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        growthRecordService.delete(recordId, user);
        return ResponseEntity.noContent().build();
    }
}
