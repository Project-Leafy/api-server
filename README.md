# api-server
# 🌿 Leafy - Backend (Spring Boot)

> 반려식물 성장 관리 및 AI 진단/추천 서비스 'Leafy'의 백엔드 레포지토리입니다.

## 1. 📜 프로젝트 개요

'Leafy'는 식물 입문자들이 겪는 식별의 어려움과 관리의 막막함을 AI 기술로 해결하는 모바일 앱입니다.사용자는 사진 한 장으로 식물을 식별하고, AI 진단을 받으며, 개인화된 성장 일지를 바탕으로 스마트한 관리 알림을 받아 식물과 함께 성장하는 즐거움을 경험할 수 있습니다.

---

## 2. 🏗️ 시스템 아키텍처 (System Architecture)

<img width="1047" height="689" alt="image" src="https://github.com/user-attachments/assets/2d44e5af-b3dc-4660-a736-0f783f5fe265" />
<img width="731" height="514" alt="image" src="https://github.com/user-attachments/assets/28b4afa4-1832-42f4-bcb7-68ad79b767ee" />

### 아키텍처 흐름
1.  **CI/CD**: 개발자가 `main` 또는 `develop` 브랜치에 Push/Merge하면, **GitHub Actions**가 이를 감지하여 코드를 빌드, 테스트, 도커라이징한 후 **AWS ECR**(Container Registry)에 푸시하고 EC2에 배포합니다.
2.  **사용자 요청**: 사용자의 요청은 **Route 53**(DNS)를 통해 VPC의 **Internet Gateway**로 진입합니다.
3.  **트래픽 제어**: **Nginx**(Reverse Proxy)가 Public Subnet의 EC2 인스턴스에서 가장 먼저 요청을 받아, 로드 밸런싱 및 SSL 처리 후 내부 Spring Boot 애플리케이션으로 트래픽을 전달합니다.
4.  **애플리케이션 로직**: **Spring Boot**(Docker) 애플리케이션이 비즈니스 로직을 처리합니다.
5.  **데이터 및 보안**:
    **AWS RDS (PostgreSQL)**: Private Subnet에 위치하며, EC2 인스턴스만 접근할 수 있습니다.
    **Amazon S3**: 사용자가 업로드하는 모든 이미지(성장일지, 진단 사진 등)를 저장합니다.
    **AWS Secrets Manager**: DB 접속 정보, Plant.id API 키, 카카오 API 키 등 모든 민감 정보를 안전하게 저장하며, Spring Boot 앱은 IAM Role을 통해 이 정보들을 런타임에 주입받습니다.

---

## 3. 💻 기술 스택 (Tech Stack)

| 구분 | 기술 | 비고 |
| :--- | :--- | :--- |
| **Framework** | Spring Boot 3.x | |
| **Language** | Java 17 | |
| **Database** | PostgreSQL | |
| **ORM** | Spring Data JPA | |
| **Container** | Docker | Nginx, Spring Boot |
| **Reverse Proxy** | Nginx | |
| **API Docs** | Swagger (SpringDoc) | |
| **CI/CD** | GitHub Actions | |
| **Cloud (AWS)** | EC2, RDS, S3, Route 53 | |
| | VPC, Secrets Manager | |

---

## 4. 🚀 로컬 개발 환경 설정 (Getting Started)

### 1. 레포지토리 클론
```bash
git clone [YOUR_REPO_URL]
cd backend-repo
```
### 2. 로컬 PostgreSQL 데이터베이스 설정
postgresql을 설치하고 실행합니다.

leafy_dev 이름의 데이터베이스를 생성합니다. (UTF-8 인코딩)

## 3. .env 파일 생성 (로컬 환경 변수)
프로젝트 루트 디렉토리(.gitignore와 같은 위치)에 .env 파일을 새로 생성합니다.(노션에서 내용 복사) 이 파일은 로컬 PC에서만 사용됩니다.

```Bash

# .env (로컬 개발용 비밀 키)
DB_URL=jdbc:postgresql://localhost:5432/leafy_dev
DB_USERNAME=[로컬_DB_사용자명]
DB_PASSWORD=[로컬_DB_비밀번호]
PLANT_ID_KEY=[Plant.id API 키]
KAKAO_API_KEY=[카카오 API 키]
JWT_SECRET_KEY=[로컬 테스트용 JWT 시크릿 키]
4. application-local.yml 설정 (환경 변수 참조)
src/main/resources/ 경로의 application-local.yml 파일이 .env 파일의 값을 참조하도록 수정합니다.
```
```YAML

# src/main/resources/application-local.yml
spring:
  # --- 1. 로컬 DB 설정 ---
  # .env 파일의 값을 ${...}로 참조합니다.
  datasource:
    driver-class-name: org.postgresql.Driver
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  # --- 2. JPA 설정 ---
  jpa:
    hibernate:
      ddl-auto: create # (주의: 초기 개발 시 'create', 이후 'validate' 또는 'none')
    properties:
      hibernate:
        format_sql: true
        show_sql: true
    open-in-view: false

  # --- 3. 로컬용 비밀 키 설정 ---
  # (AWS Secrets Manager 대신 .env 파일의 값을 사용)
secrets:
  plant-id-key: ${PLANT_ID_KEY}
  kakao-api-key: ${KAKAO_API_KEY}
  jwt-secret-key: ${JWT_SECRET_KEY}
```
## 5. 애플리케이션 실행
```Bash

./gradlew bootRun
```
gradlew가 실행되면서 gradle-dotenv 플러그인이 .env 파일을 읽어 application-local.yml에 정의된 변수들을 자동으로 주입해 줍니다.

## 4. 애플리케이션 실행
```Bash

./gradlew bootRun
```
## 5. 📖 API 문서 (Swagger)
로컬 환경: http://localhost:8080/swagger-ui.html

개발 서버: [개발 서버 배포 후 Swagger URL 삽입]

## 6. 🤝 협업 규칙 (Git Flow & Commit)
### 1. 브랜치 전략 (Git Flow)
main: 릴리즈(배포)용 브랜치.

develop: 개발의 중심이 되는 브랜치. (PR의 Target 브랜치)

feat/기능명 or 이름: 기능 개발을 위한 브랜치. (예: feature/login)

hotfix/이슈명: 긴급 버그 수정 브랜치.

### 2. 커밋 메시지 (Commit Convention)
feat: 새로운 기능 추가

fix: 버그 수정

docs: 문서 수정 (README.md 등)

style: 코드 스타일 수정 (포맷팅, 세미콜론 등)

refactor: 코드 리팩토링

test: 테스트 코드 추가/수정

chore: 빌드 설정, 의존성 추가 등 기타 작업


예시: git commit -m "feat: 카카오 소셜 로그인 API 구현" 

### 3. Pull Request (PR) 및 코드 리뷰
기능 개발(feature/*)이 완료되면, develop 브랜치로 **Pull Request(PR)**를 생성합니다.

팀원 최소 1명 이상의 **Approve(승인)**를 받아야만 develop 브랜치로 병합(Merge)할 수 있습니다.
