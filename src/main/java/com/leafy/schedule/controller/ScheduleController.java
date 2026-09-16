package com.leafy.schedule.controller;

import com.leafy.schedule.dto.ScheduleRequest;
import com.leafy.schedule.dto.ScheduleResponse;
import com.leafy.schedule.dto.ScheduleSetupRequest; // DTO 임포트
import com.leafy.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Slf4j 임포트
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j // 롬복 Slf4j 어노테이션 추가
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> getMySchedules() {
        return ResponseEntity.ok(scheduleService.getMySchedules());
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> addSchedule(@RequestBody ScheduleRequest request) {
        // [수정] 서버 로그 추가
        log.info("[API] 단일 일정 추가 요청: {}", request);
        scheduleService.addSchedule(request);

        Map<String, String> response = new HashMap<>();
        response.put("message", "일정이 성공적으로 추가되었습니다.");
        return ResponseEntity.ok(response);
    }
    
    // [추가] 식물 등록 직후 초기 스케줄 일괄 생성을 위한 API
    @PostMapping("/initial-setup")
    public ResponseEntity<Map<String, String>> createInitialSchedules(@RequestBody List<ScheduleSetupRequest> requests) {
        log.info("[API] 초기 일정 일괄 생성 요청: {}건", requests.size());
        // 서비스 레이어에 일괄 처리 위임
        scheduleService.createInitialSchedules(requests);

        Map<String, String> response = new HashMap<>();
        response.put("message", "초기 관리 일정이 성공적으로 설정되었습니다.");
        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.noContent().build();
    }
}