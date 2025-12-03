package com.leafy.plant.controller;

import com.leafy.plant.service.NongsaroBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin API", description = "관리자 전용 기능")
public class AdminController {

    private final NongsaroBatchService nongsaroBatchService;

    @Operation(summary = "농사로 식물 데이터 배치 처리", description = "농사로 API에서 식물 데이터를 가져와 DB에 Upsert(생성/업데이트)합니다.")
    @PostMapping("/plants/batch-process")
    public ResponseEntity<String> processNongsaroPlantsBatch() {
        log.info("관리자 요청에 의해 농사로 식물 데이터 배치 처리를 시작합니다.");
        nongsaroBatchService.fetchAllAndSave();
        return ResponseEntity.ok("농사로 식물 데이터 배치 처리가 완료되었습니다.");
    }
}