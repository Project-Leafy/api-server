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
FROM amazoncorretto:17-alpine
WORKDIR /app

# 타임존을 고정한다.
# alpine 기본값은 UTC 라서, 지정하지 않으면 로그 타임스탬프가 +00:00 으로 찍힌다.
# 수집 계층·대시보드와 시각 해석이 어긋나므로 명시한다. (TZ 환경변수로 덮어쓸 수 있다)
RUN apk add --no-cache tzdata
ENV TZ=Asia/Seoul

# 빌드 단계에서 생성된 jar 파일만 가져옴
COPY --from=builder /app/build/libs/*.jar app.jar

# 8080 = 애플리케이션, 9090 = 모니터링(Actuator)
# 9090 은 내부망 전용이므로 호스트로 publish 하지 말 것.
EXPOSE 8080
EXPOSE 9090

# 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]
