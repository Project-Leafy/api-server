package com.leafy.plant.repository;

import com.leafy.plant.domain.MyPlant;
import com.leafy.user.domain.User; // User 엔티티 import
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MyPlantRepository extends JpaRepository<MyPlant, Long> {

    // 1. 특정 사용자가 등록한 '내 식물' 목록 전체 조회 (메인 홈)
    List<MyPlant> findAllByUser(User user);
    
    // 2. (대안) 사용자의 ID로 직접 조회
    // List<MyPlant> findAllByUserId(Long userId);

    // 3. 특정 사용자가 소유한 식물의 총 개수 (users 테이블의 집계 데이터 업데이트 시)
    long countByUser(User user);

    // User 객체를 기준으로 모든 MyPlant를 찾아서 생성일(createdAt) 기준 내림차순으로 정렬
    List<MyPlant> findAllByUserOrderByCreatedAtDesc(User user);

    // [추가] 입양일(adoptionDate)이 특정 날짜 리스트에 포함되는 식물들 조회
    // 용도: 오늘이 D+3, D+100 등에 해당하는 식물을 한 번에 찾기 위함
    List<MyPlant> findAllByAdoptionDateIn(List<LocalDate> dates);
}