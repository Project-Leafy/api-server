package com.leafy.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 로컬 디스크에 저장된 업로드 파일을 HTTP로 서빙한다.
 *
 * <p>기존에는 S3에 올리고 CloudFront가 서빙했다. 그 역할을 앱이 대신한다.
 * 업로드 API가 돌려주는 URL이 이 경로로 열린다.
 */
@Configuration
public class FileStorageWebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.url-path}")
    private String urlPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(urlPath + "/**")
                .addResourceLocations(location);
    }
}
