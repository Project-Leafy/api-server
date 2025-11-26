-- 1. 테스트 사용자 (User) 데이터 삽입 (식물 개수 2개로 수정)
INSERT INTO users (user_id, email, nickname, oauth_provider, current_plants_count, onboarding_status, role, created_at, updated_at) VALUES
(1, 'oksorry12@naver.com', '권준희', 'kakao', 2, false, 'USER', NOW(), NOW());

-- 2. 테스트 식물 종 (PlantSpecies) 데이터 삽입

-- 101: 몬스테라 (상세 정보 포함)
INSERT INTO plant_species (
    species_id, scientific_name, korean_name, family_name, genus_name,
    watering_cycle_code, sunlight_level_code, difficulty_level, size_code,
    is_pet_friendly, optimal_temp_celsius,
    management_tip_detail, toxicity_info,
    is_verified_by_admin, created_at, updated_at
) VALUES (
    101, 'Monstera Deliciosa', '몬스테라', '천남성과', '몬스테라속',
    'NORMAL', 'MEDIUM', 'EASY', 'LARGE',
    false, '20~25°C',
    '몬스테라는 덩굴성 식물이라 지지대를 세워주면 더 크게 자랍니다. 잎이 갈라지기 위해서는 충분한 간접광이 필요해요. 공중 뿌리는 잘라내지 말고 흙으로 유도해주면 영양 흡수에 도움이 됩니다.',
    '잎과 줄기에 옥살산 칼슘이 있어 반려동물이 섭취 시 구토나 입안 통증을 유발할 수 있으니 주의가 필요합니다.',
    true, NOW(), NOW()
);

-- 102: 스투키 (상세 정보 포함)
INSERT INTO plant_species (
    species_id, scientific_name, korean_name, family_name, genus_name,
    watering_cycle_code, sunlight_level_code, difficulty_level, size_code,
    is_pet_friendly, optimal_temp_celsius,
    management_tip_detail, toxicity_info,
    is_verified_by_admin, created_at, updated_at
) VALUES (
    102, 'Sansevieria Cylindrica', '스투키', '백합과', '산세베리아속',
    'RARE', 'LOW', 'EASY', 'SMALL',
    false, '18~27°C',
    '물을 너무 자주 주면 뿌리가 썩을 수 있습니다. 흙이 바짝 말랐을 때, 혹은 한 달에 한 번 정도만 물을 주세요. 전자파 차단과 공기 정화 능력이 뛰어난 식물입니다.',
    '반려동물이 섭취할 경우 가벼운 배탈을 일으킬 수 있습니다.',
    true, NOW(), NOW()
);


-- 3. 나의 식물 (MyPlant) 데이터 삽입

-- 1번: 'choco' (몬스테라)
INSERT INTO my_plant (plant_id, user_id, species_id, nickname, adoption_date, image_url, last_watered_date, status_code, created_at, updated_at) VALUES
(1, 1, 101, 'choco', '2024-01-15', 'https://leafy-s3-bucket.s3.ap-northeast-2.amazonaws.com/leafy_test_2.jpg', '2024-05-20', 'HEALTHY', NOW(), NOW());

-- 2번: 'banana' (스투키)
INSERT INTO my_plant (plant_id, user_id, species_id, nickname, adoption_date, image_url, last_watered_date, status_code, created_at, updated_at) VALUES
(2, 1, 102, 'banana', '2024-03-10', 'https://leafy-s3-bucket.s3.ap-northeast-2.amazonaws.com/leafy_test.jpg', '2024-05-15', 'HEALTHY', NOW(), NOW());


-- 4. 시퀀스(Auto Increment) 재설정 (필수)
-- 이미 1, 2번 데이터를 넣었으므로, 다음 데이터는 100번부터 시작하도록 설정
ALTER TABLE my_plant ALTER COLUMN plant_id RESTART WITH 100;
ALTER TABLE users ALTER COLUMN user_id RESTART WITH 100;
-- species는 101, 102번을 썼으므로 200번부터 시작하도록 설정
ALTER TABLE plant_species ALTER COLUMN species_id RESTART WITH 200;