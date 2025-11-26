package com.leafy.plant.service;

import com.leafy.plant.dto.MyPlantResponseDto;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.domain.PlantSpecies;
import com.leafy.plant.dto.CreateMyPlantRequest;
import com.leafy.plant.repository.PlantSpeciesRepository;
import com.leafy.global.exception.EntityNotFoundException;
import java.time.LocalDate;
import com.leafy.schedule.service.ScheduleService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPlantService {

    private final MyPlantRepository myPlantRepository;
    private final PlantSpeciesRepository plantSpeciesRepository;
    private final ScheduleService scheduleService;

    public List<MyPlantResponseDto> findMyPlants(User user) {
        return myPlantRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(MyPlantResponseDto::from)
                .collect(Collectors.toList());
    }
    /**
     * 식물 최종 등록 (저장)
     */
    @Transactional
    public MyPlantResponseDto join(User user, CreateMyPlantRequest request) {
        // 1. 식물 종 정보 조회 (없으면 예외 발생)
        PlantSpecies species = plantSpeciesRepository.findById(request.speciesId())
                .orElseThrow(() -> new EntityNotFoundException("Plant Species not found with id: " + request.speciesId()));

        // 2. 입양일 설정 (입력 없으면 오늘 날짜)
        LocalDate adoptionDate = request.adoptionDate() != null ? request.adoptionDate() : LocalDate.now();

        // 3. 내 식물(MyPlant) 객체 생성
        MyPlant myPlant = MyPlant.builder()
                .user(user)
                .plantSpecies(species)
                .nickname(request.nickname())
                .imageUrl(request.imageUrl())
                .adoptionDate(adoptionDate)
                .build();

        // 4. DB 저장
        MyPlant savedPlant = myPlantRepository.save(myPlant);

        // 5. 스케줄 자동 생성 호출!
        scheduleService.createInitialSchedule(savedPlant);

        return MyPlantResponseDto.from(savedPlant);
    }

    /**
     * 식물 삭제 (소유자 확인 포함)
     */
    @Transactional
    public void delete(Long plantId, User user) {
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new EntityNotFoundException("Plant not found with id: " + plantId));

        // 내 식물이 맞는지 확인
        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("User does not own this plant.");
        }

        myPlantRepository.delete(myPlant);
    }

    /**
     * 식물 상세 조회
     */
    public MyPlantResponseDto getMyPlantDetail(Long plantId) {
        // DB에서 ID로 조회, 없으면 에러 발생
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new EntityNotFoundException("해당 식물을 찾을 수 없습니다. ID: " + plantId));

        // DTO로 변환하여 반환 (MyPlantResponseDto.from 메서드 안에서 상세 정보 매핑됨)
        return MyPlantResponseDto.from(myPlant);
    }
}
