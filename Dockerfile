# 1. 빌드 단계 (Gradle을 사용해 소스코드를 실행 가능한 jar 파일로 만듦)
FROM gradle:8-jdk17 AS builder
WORKDIR /app

# 캐시 효율을 위해 의존성 설정 파일만 먼저 복사
COPY build.gradle settings.gradle ./
# (만약 settings.gradle이 없다면 위 줄에서 settings.gradle은 지워주세요)

# 소스 코드 복사
COPY src ./src

# Gradle 빌드 실행 (테스트는 건너뛰고 빌드하여 속도 향상)
RUN gradle bootJar -x test --no-daemon

# 2. 실행 단계 (가벼운 Java 실행 환경만 포함)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드 단계에서 생성된 jar 파일만 가져옴
COPY --from=builder /app/build/libs/*.jar app.jar

# 컨테이너가 8080 포트를 쓴다는 것을 명시
EXPOSE 8080

# 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]
