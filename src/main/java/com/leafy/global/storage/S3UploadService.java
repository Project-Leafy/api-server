package com.leafy.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3UploadService {

    // 1. AWS SDK v2의 S3Client
    // (application-dev.yaml의 spring.cloud.aws 설정으로 자동 Bean 등록됨)
    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${cloudfront.url}")
    private String cloudfrontUrl;

    /**
     * MultipartFile을 S3에 업로드하고 CloudFront URL을 반환 (AWS SDK v2 사용)
     * (임시 파일 생성 X)
     */
    public String upload(MultipartFile multipartFile, String dirName) throws IOException {

        // 1. S3에 저장할 파일 이름 생성 (중복 방지)
        String fileName = dirName + "/" + UUID.randomUUID() + "_" + multipartFile.getOriginalFilename();

        // 2. AWS SDK v2 스타일로 PutObjectRequest 생성
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                // .acl(ObjectCannedACL.PUBLIC_READ) <--- 이 부분을 삭제해야 합니다!
                .contentType(multipartFile.getContentType()) // 파일 타입 설정
                .build();

        // 3. InputStream을 RequestBody로 만들어 S3에 업로드
        try (InputStream inputStream = multipartFile.getInputStream()) {
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(inputStream, multipartFile.getSize()));
        }

        // 4. CloudFront URL 반환 (예: https://.../diagnosis/uuid_image.jpg)
        return cloudfrontUrl + "/" + fileName;
    }
}
