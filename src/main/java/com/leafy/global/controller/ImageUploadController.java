package com.leafy.global.controller; // 패키지 경로는 상황에 맞게 조정

import com.leafy.global.storage.S3UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 혹시 CORS 에러가 나면 이거 추가!
public class ImageUploadController {

    private final S3UploadService s3UploadService;

    // 성장 일지 등 일반 이미지 업로드용 API
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestPart("file") MultipartFile file
    ) throws IOException {

        // 1. S3에 업로드 (디렉토리명을 "journal"로 설정하여 구분)
        String s3Url = s3UploadService.upload(file, "journal");
        log.info("Journal Image uploaded: {}", s3Url);

        // 2. URL 반환 (JSON 형식)
        Map<String, String> response = new HashMap<>();
        response.put("imageUrl", s3Url);

        return ResponseEntity.ok(response);
    }
}