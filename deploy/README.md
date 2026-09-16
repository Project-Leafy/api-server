# Leafy 배포 (Windows 데스크톱 + Tailscale)

도커허브 이미지(`gicks/ai-ops-leafy-backend`, `gicks/ai-ops-leafy-frontend`)를 받아 실행한다.
같은 tailnet 에 있는 팀원만 `http://<Tailscale IP>` 로 접속할 수 있다.

| 구성 | 위치 | 밖으로 열린 포트 |
|---|---|---|
| 프론트 (nginx) | 컨테이너 | Tailscale IP 의 80 |
| 백엔드 | 컨테이너 | 없음 (nginx 가 `/api` 로 넘김) |
| MinIO (사진 저장소) | 컨테이너 | 없음 (nginx 가 `/files` 로 넘김) |
| PostgreSQL | Windows 에 직접 설치 | 5432 (이 PC 안에서만) |

## 1. 준비물

- Docker Desktop
- Tailscale (로그인 후 Connected 상태)
- PostgreSQL 17 (설치할 때 정한 `postgres` 관리자 비밀번호를 기억해 둔다)

## 2. DB 준비 (처음 한 번)

PowerShell 에서 실행한다. 새 비밀번호를 물으면 `.env` 의 `DB_PASSWORD` 로 쓸 값을 넣는다.

```powershell
& "C:\Program Files\PostgreSQL\17\bin\createuser.exe" -h 127.0.0.1 -U postgres -P leafy_user
& "C:\Program Files\PostgreSQL\17\bin\createdb.exe"   -h 127.0.0.1 -U postgres -O leafy_user leafy_dev
```

## 3. 설정 파일

이 `deploy` 폴더에서 `.env.example` 을 `.env` 로 복사하고 빈 값을 채운다.
`TAILSCALE_IP` 는 Tailscale 앱의 이 기기 IPv4 주소다.

## 4. 실행

```powershell
docker compose pull
docker compose up -d
docker compose logs -f backend
```

`Started LeafyApplication` 이 보이면 기동된 것이다.

### DB 연결이 거부될 때

로그에 `no pg_hba.conf entry for host "X.X.X.X"` 가 보이면, 컨테이너의 접속을 PostgreSQL 이 막은 것이다.
관리자 권한으로 `C:\Program Files\PostgreSQL\17\data\pg_hba.conf` 맨 아래에 그 주소를 허용하는 줄을 넣고
Windows 서비스에서 `postgresql-x64-17` 을 재시작한다.

```
host    leafy_dev    leafy_user    X.X.X.X/32    scram-sha-256
```

## 5. 식물 도감 데이터 넣기 (처음 한 번)

백엔드가 한 번 기동해 테이블이 만들어진 뒤 실행한다. 여러 번 실행해도 중복되지 않는다.

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -h 127.0.0.1 -U leafy_user -d leafy_dev -f ..\db\seed\plant_species.sql
```

도감 이미지 217장은 백엔드가 기동할 때 농사로 원본에서 자동으로 받아 MinIO 에 올린다.
완료되면 로그에 `[PlantImages] 도감 이미지 복구 완료` 가 남는다. 이미 받은 이미지는 다시 받지 않는다.

## 6. 접속 확인

팀원 PC(같은 tailnet)에서 `http://<TAILSCALE_IP>` 를 연다. 회원가입 후 로그인한다.

## 업데이트

```powershell
docker compose pull
docker compose up -d
```

## 데이터 백업

- 사진: `leafy_minio_data` 도커 볼륨. `docker compose down -v` 는 이 볼륨을 **삭제**하므로 쓰지 않는다.
- DB: `pg_dump -h 127.0.0.1 -U leafy_user leafy_dev > leafy_backup.sql`

## 카카오 로그인을 다시 켜려면

`SPRING_PROFILES_ACTIVE` 를 `dev,kakao` 로 바꾸고 `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`,
`KAKAO_REDIRECT_URI` 를 넣는다. 켜면 알림도 앱 안 알림함 대신 카카오톡으로 발송된다.
