package com.leafy.schedule.controller;

import com.leafy.schedule.dto.ScheduleRequest;
import com.leafy.schedule.dto.ScheduleResponse;
import com.leafy.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> getMySchedules() {
        // @AuthenticationPrincipal 제거 - Service에서 SecurityContextHolder로 처리
        return ResponseEntity.ok(scheduleService.getMySchedules());
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> addSchedule(@RequestBody ScheduleRequest request) {
        // ✅ 디버깅 로그 추가
        System.out.println("===== 받은 데이터 =====");
        System.out.println("plantId: " + request.getPlantId());
        System.out.println("scheduleType: " + request.getScheduleType());
        System.out.println("nextDueDate: " + request.getNextDueDate());
        System.out.println("=====================");
        // @AuthenticationPrincipal 제거 - Service에서 SecurityContextHolder로 처리
        scheduleService.addSchedule(request);

        Map<String, String> response = new HashMap<>();
        response.put("message", "일정이 성공적으로 추가되었습니다.");
        return ResponseEntity.ok(response);
    }

    // ✅ 일정 삭제 API 추가
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.noContent().build();
    }
}