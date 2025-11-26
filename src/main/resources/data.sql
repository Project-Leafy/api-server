-- 1. 테스트 유저 생성
INSERT INTO users (email, nickname, role, current_plants_count, onboarding_status, created_at)
VALUES ('test@test.com', '테스터', 'USER', 1, true, CURRENT_TIMESTAMP);

-- 2. [중요] 식물 종(Species) 먼저 등록! (ID: 1)
INSERT INTO plant_species (species_id, scientific_name, korean_name, watering_cycle_code, sunlight_level_code, difficulty_level, size_code, is_verified_by_admin, created_at)
VALUES (1, 'Monstera deliciosa', '몬스테라', 'NORMAL', 'MEDIUM', 'EASY', 'MEDIUM', true, CURRENT_TIMESTAMP);

-- 3. 내 식물(MyPlant) 등록 (ID: 1 식물 종 참조)
INSERT INTO my_plant (user_id, nickname, species_id, adoption_date, status_code, created_at)
VALUES (1, '테스트식물', 1, CURRENT_DATE, 'HEALTHY', CURRENT_TIMESTAMP);

-- 4. 오늘 날짜 스케줄 생성 (알림 테스트용)
INSERT INTO schedule (plant_id, schedule_type, next_due_date, frequency_days, notification_status, created_at)
VALUES (1, 'WATERING', CURRENT_DATE, 7, 'PENDING', CURRENT_TIMESTAMP);

-- 5. 진단 기록 (DiagnosisHistory ID: 1)
-- 테스트 시나리오: 오늘이 D+2 (팁 발송일)인 상태로 만듭니다.
INSERT INTO diagnosis_history (
    plant_id,
    diagnosis_datetime,
    request_image_url,
    is_plant_probability,
    is_healthy,
    disease_name,
    disease_probability,
    feedback_step,
    tip_date,
    check_date,
    created_at,
    updated_at
)
VALUES (
    1,                   -- plant_id
    CURRENT_TIMESTAMP(), -- diagnosis_datetime
    'https://via.placeholder.com/150', -- request_image_url
    0.98,                -- is_plant_probability
    false,               -- is_healthy (아픔)
    'Leaf Spot',         -- disease_name (잎 반점병)
    0.85,                -- disease_probability
    'NONE',              -- feedback_step (아직 발송 전)
    CURRENT_DATE(),      -- tip_date (오늘 날짜 -> 스케줄러가 잡음)
    DATEADD('DAY', 5, CURRENT_DATE()), -- check_date (5일 뒤)
    CURRENT_TIMESTAMP(),
    CURRENT_TIMESTAMP()
);