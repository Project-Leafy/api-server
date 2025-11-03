-- 1. 테스트 사용자 (User) 데이터 삽입
INSERT INTO users (user_id, email, nickname, oauth_provider, current_plants_count, onboarding_status, role, created_at, updated_at) VALUES
(1, 'oksorry12@naver.com', '권준희', 'kakao', 0, false, 'USER', NOW(), NOW());

-- 2. 테스트 식물 종 (PlantSpecies) 데이터 삽입
INSERT INTO plant_species (species_id, scientific_name, korean_name, watering_cycle_code, sunlight_level_code, is_verified_by_admin, created_at, updated_at) VALUES
(101, 'Monstera Deliciosa', '몬스테라', '주 1회', '밝은 간접광', false, NOW(), NOW());

-- 3. 나의 식물 (MyPlant) 데이터 삽입
INSERT INTO my_plant (plant_id, user_id, species_id, nickname, adoption_date, status_code, created_at, updated_at) VALUES
(1, 1, 101, '테스트 반려 1호', NOW(), 'HEALTHY', NOW(), NOW());