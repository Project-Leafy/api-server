-- 1. 테스트 사용자 (User) 데이터 삽입
INSERT INTO users (user_id, email, nickname, oauth_provider, current_plants_count, onboarding_status, role, created_at, updated_at) VALUES
(1, 'oksorry12@naver.com', '권준희', 'kakao', 0, false, 'USER', NOW(), NOW());

-- 2. 테스트 식물 종 (PlantSpecies) 데이터 삽입
-- 101: 몬스테라 (주 1회 -> NORMAL / 밝은 간접광 -> MEDIUM / 난이도 -> EASY / 크기 -> LARGE)
INSERT INTO plant_species (
    species_id, scientific_name, korean_name,
    watering_cycle_code, sunlight_level_code,
    difficulty_level, size_code,
    is_verified_by_admin, created_at, updated_at
) VALUES (
    101, 'Monstera Deliciosa', '몬스테라',
    'NORMAL', 'MEDIUM',
    'EASY', 'LARGE',
    false, NOW(), NOW()
);

-- 102: 스투키 (월 1회 -> RARE / 반양지 -> LOW / 난이도 -> EASY / 크기 -> SMALL)
INSERT INTO plant_species (
    species_id, scientific_name, korean_name,
    watering_cycle_code, sunlight_level_code,
    difficulty_level, size_code,
    is_verified_by_admin, created_at, updated_at
) VALUES (
    102, 'Sansevieria trifasciata', '스투키',
    'RARE', 'LOW',
    'EASY', 'SMALL',
    true, NOW(), NOW()
);

-- 3. 나의 식물 (MyPlant) 데이터 삽입
INSERT INTO my_plant (plant_id, user_id, species_id, nickname, adoption_date, image_url, last_watered_date, status_code, created_at, updated_at) VALUES
(1, 1, 101, 'choco', '2024-01-15', 'https://leafy-s3-bucket.s3.ap-northeast-2.amazonaws.com/leafy_test_2.jpg', '2024-05-20', 'HEALTHY', NOW(), NOW()),
(2, 1, 102, 'banana', '2024-03-10', 'https://leafy-s3-bucket.s3.ap-northeast-2.amazonaws.com/leafy_test.jpg', '2024-05-15', 'HEALTHY', NOW(), NOW());

-- H2 Database 문법 기준 (현재 H2를 쓰고 계십니다)
ALTER TABLE my_plant ALTER COLUMN plant_id RESTART WITH 100;
ALTER TABLE users ALTER COLUMN user_id RESTART WITH 100;
-- (만약 users 테이블도 test data가 있다면 위 줄도 필요할 수 있음)