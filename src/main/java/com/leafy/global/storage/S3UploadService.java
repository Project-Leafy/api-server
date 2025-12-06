package com.leafy.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${cloudfront.url}")
    private String cloudfrontUrl;

    /**
     * MultipartFile을 S3에 업로드하고 CloudFront URL을 반환 (AWS SDK v2 사용)
     */
    public String upload(MultipartFile multipartFile, String dirName) throws IOException {

        // 1. S3에 저장할 파일 이름 생성 (UUID로 중복 방지)
        String originalFilename = multipartFile.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String fileName = dirName + "/" + UUID.randomUUID() + extension;

        // 2. PutObjectRequest 생성 → ACL 설정 완전히 삭제!
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(multipartFile.getContentType())
                .contentLength(multipartFile.getSize())  // 권장 (성능 향상 + 정확도)
                // .acl(ObjectCannedACL.PUBLIC_READ)  ← 이 라인 완전히 삭제했습니다!
                .build();

        // 3. 업로드
        try (InputStream inputStream = multipartFile.getInputStream()) {
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(inputStream, multipartFile.getSize()));
        }

        // 4. CloudFront URL 반환
        return cloudfrontUrl + "/" + fileName;
    }
}