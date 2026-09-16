package com.leafy.plant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dictionary")
@RequiredArgsConstructor
public class DictionaryController {

    // S3/CloudFront 를 걷어내고 앱이 직접 서빙하므로, 업로드 저장소의 공개 URL을 쓴다.
    @Value("${app.storage.public-base-url}")
    private String publicBaseUrl;

    // 프론트엔드가 호출할 API
    @GetMapping("/url")
    public ResponseEntity<String> getDictionaryUrl() {
        // 고정된 파일명(final_plants.json)의 전체 경로를 반환
        String fullUrl = publicBaseUrl + "/plants/final_plants.json";
        return ResponseEntity.ok(fullUrl);
    }
}