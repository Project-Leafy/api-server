package com.leafy.global.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 업로드 파일을 로컬 디스크에 저장한다.
 *
 * <p>이 앱은 공개 노출하지 않기로 했으므로 S3·CloudFront 연동을 걷어내고
 * 로컬 디스크로 대체했다. 외부 자격증명이 사라져 키 유출 위험이 줄고,
 * 네트워크 실패 지점도 하나 줄어든다.
 *
 * <p>저장 경로는 {@code app.upload.dir} 로 설정한다. 컨테이너를 재생성해도
 * 파일이 남으려면 이 경로에 볼륨을 물려야 한다.
 */
@Slf4j
@Service
public class FileStorageService {

    private final Path rootDir;
    private final String publicBaseUrl;

    public FileStorageService(
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${app.upload.public-base-url}") String publicBaseUrl) {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        // 뒤 슬래시가 중복되지 않도록 정리한다.
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(rootDir);
            log.info("[Storage] 업로드 저장 경로: {}", rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉터리를 생성할 수 없습니다: " + rootDir, e);
        }
    }

    /** MultipartFile 저장. 이름은 UUID로 만들어 중복과 경로 조작을 모두 피한다. */
    public String upload(MultipartFile multipartFile, String dirName) throws IOException {
        String extension = extensionOf(multipartFile.getOriginalFilename(), "");
        String key = dirName + "/" + UUID.randomUUID() + extension;

        try (InputStream in = multipartFile.getInputStream()) {
            return store(in, key);
        }
    }

    /** 외부에서 받아온 스트림 저장 (예: 농사로 이미지). */
    public String upload(InputStream inputStream, String originalFileName,
                         long contentLength, String contentType, String dirName) {
        String extension = extensionOf(originalFileName, ".jpg");
        String key = dirName + "/" + UUID.randomUUID() + extension;
        return store(inputStream, key);
    }

    /** 파일 이름을 그대로 쓰는 저장 (덮어쓰기 대상 데이터 파일 등). */
    public String uploadFixed(InputStream inputStream, String fileName,
                              long contentLength, String contentType, String folder) {
        String key = (folder != null && !folder.isEmpty()) ? folder + "/" + fileName : fileName;
        return store(inputStream, key);
    }

    /** 저장된 파일을 읽는다. 없으면 null. */
    public InputStream read(String key) throws IOException {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            return null;
        }
        return Files.newInputStream(target);
    }

    /** 바이트 배열을 그대로 저장(덮어쓰기)한다. */
    public void write(String key, byte[] content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private String store(InputStream inputStream, String key) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패: " + key, e);
        }
        return publicBaseUrl + "/" + key;
    }

    /**
     * 저장 경로를 만든다.
     *
     * <p>{@code ../} 같은 입력으로 저장 디렉터리 바깥에 쓰는 것을 막는다.
     * 파일 이름은 UUID로 생성하지만, dirName·folder 는 호출자가 넘기므로 확인이 필요하다.
     */
    private Path resolve(String key) {
        Path target = rootDir.resolve(key).normalize();
        if (!target.startsWith(rootDir)) {
            throw new IllegalArgumentException("허용되지 않는 경로입니다: " + key);
        }
        return target;
    }

    private String extensionOf(String fileName, String fallback) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return fallback;
    }
}
