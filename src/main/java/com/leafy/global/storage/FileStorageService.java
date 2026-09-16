package com.leafy.global.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 업로드 파일을 MinIO 에 저장한다.
 *
 * <p>파일은 브라우저가 앱을 거치지 않고 nginx → MinIO 로 바로 받아간다.
 * 그래서 돌려주는 URL 은 {@code /files/<key>} 형태의 상대 경로다.
 * 접속 주소(IP·도메인)가 바뀌어도 URL 을 고칠 필요가 없다.
 */
@Slf4j
@Service
public class FileStorageService {

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public FileStorageService(
            S3Client storageClient,
            @Value("${app.storage.bucket}") String bucket,
            @Value("${app.storage.public-base-url}") String publicBaseUrl) {
        this.s3 = storageClient;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    /**
     * 버킷이 없으면 만들고, 파일 읽기(GetObject)만 익명으로 허용한다.
     * 목록 조회·쓰기·삭제는 허용하지 않는다.
     *
     * <p>MinIO 가 아직 뜨지 않았더라도 앱 기동은 막지 않는다. 첫 업로드 때 다시 실패가 드러난다.
     */
    @PostConstruct
    void init() {
        try {
            if (!bucketExists()) {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("[Storage] 버킷 생성: {}", bucket);
            }
            s3.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(bucket)
                    .policy(publicReadPolicy())
                    .build());
            log.info("[Storage] 저장소 준비 완료 bucket={}", bucket);
        } catch (Exception e) {
            log.error("[Storage] 저장소 초기화 실패 bucket={} ({})", bucket, e.getMessage());
        }
    }

    /** MultipartFile 저장. 이름은 UUID 로 만들어 중복과 경로 조작을 모두 피한다. */
    public String upload(MultipartFile multipartFile, String dirName) throws IOException {
        String key = dirName + "/" + UUID.randomUUID() + extensionOf(multipartFile.getOriginalFilename(), "");
        try (InputStream in = multipartFile.getInputStream()) {
            put(key, in, multipartFile.getSize(), multipartFile.getContentType());
        }
        return urlOf(key);
    }

    /** 외부에서 받아온 스트림 저장 (예: 농사로 이미지). */
    public String upload(InputStream inputStream, String originalFileName,
                         long contentLength, String contentType, String dirName) {
        String key = dirName + "/" + UUID.randomUUID() + extensionOf(originalFileName, ".jpg");
        put(key, inputStream, contentLength, contentType);
        return urlOf(key);
    }

    /** 파일 이름을 그대로 쓰는 저장 (덮어쓰기 대상 데이터 파일 등). */
    public String uploadFixed(InputStream inputStream, String fileName,
                              long contentLength, String contentType, String folder) {
        String key = (folder != null && !folder.isEmpty()) ? folder + "/" + fileName : fileName;
        put(key, inputStream, contentLength, contentType);
        return urlOf(key);
    }

    /** 저장된 파일을 읽는다. 없으면 null. */
    public InputStream read(String key) {
        try {
            ResponseInputStream<GetObjectResponse> in =
                    s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
            return in;
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /** 바이트 배열을 그대로 저장(덮어쓰기)한다. */
    public void write(String key, byte[] content, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
    }

    private void put(String key, InputStream in, long length, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(length)
                        .build(),
                RequestBody.fromInputStream(in, length));
    }

    private String urlOf(String key) {
        return publicBaseUrl + "/" + key;
    }

    private boolean bucketExists() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    private String publicReadPolicy() {
        return """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
                "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""".formatted(bucket);
    }

    private String extensionOf(String fileName, String fallback) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return fallback;
    }
}
