package com.leafy.plant.service;

import com.fasterxml.jackson.databind.ObjectMapper; // ✅ 추가
import com.leafy.diagnosis.repository.DiagnosisHistoryRepository;
import com.leafy.plant.dto.PlantDetailResponseDto; // ✅ 추가 (반환 타입 변경)
import com.leafy.diagnosis.domain.DiagnosisHistory; // ✅ 추가 (Optional 사용)
import lombok.extern.slf4j.Slf4j; // ✅ 추가
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
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j // ✅ 추가
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPlantService {

    private final MyPlantRepository myPlantRepository;
    private final PlantSpeciesRepository plantSpeciesRepository;
    private final ScheduleService scheduleService;
    private final DiagnosisHistoryRepository diagnosisHistoryRepository; // ✅ 주입
    private final ObjectMapper objectMapper; // ✅ 주입

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
                .identificationResult(request.identificationResult()) // ✅ 추가: 식별 결과 JSON 저장
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
     * 반환 타입: MyPlantResponseDto -> PlantDetailResponseDto로 변경
     */
    public PlantDetailResponseDto getMyPlantDetail(Long plantId) {
        // 1. MyPlant 조회
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new EntityNotFoundException("해당 식물을 찾을 수 없습니다. ID: " + plantId));

        // 2. 최신 진단 기록 조회
        Optional<DiagnosisHistory> latestHistory = diagnosisHistoryRepository
                .findTopByMyPlantOrderByDiagnosisDatetimeDesc(myPlant);

        // 3. PlantDetailResponseDto의 팩토리 메서드를 사용하여 모든 정보를 취합 후 반환
        // JSON 파싱 및 최종 DTO 조립은 PlantDetailResponseDto.of()에서 처리된다 이다.
        return PlantDetailResponseDto.of(myPlant, latestHistory, objectMapper); // ✅ DTO 반환
    }
}
